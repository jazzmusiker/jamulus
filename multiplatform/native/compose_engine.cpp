/*
 * Copyright (c) 2026 Thomas Mueller
 * Author(s): Thomas Mueller
 * Licensed under AGPL 3.0 or any later version. See COPYING for details.
 */

#include "compose_engine.h"

#include <QCoreApplication>
#include <QDir>
#include <QDateTime>
#include <QFileInfo>
#include <QHash>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QMetaObject>
#include <QMutexLocker>
#include <QQueue>
#include <QRegularExpression>
#include <QSet>
#include <QStandardPaths>
#include <QTimer>

#include "../../src/client.h"
#include "../../src/settings.h"
#include "../../src/util.h"

namespace
{
QCoreApplication* g_qtApplication = nullptr;

QString FromJavaString ( JNIEnv* env, jstring value )
{
    if ( value == nullptr )
    {
        return QString();
    }

    const jchar* chars = env->GetStringChars ( value, nullptr );
    if ( chars == nullptr )
    {
        return QString();
    }

    const jsize   length = env->GetStringLength ( value );
    const QString result = QString::fromUtf16 ( reinterpret_cast<const ushort*> ( chars ), length );
    env->ReleaseStringChars ( value, chars );
    return result;
}

jstring ToJavaString ( JNIEnv* env, const QString& value )
{
    return env->NewString ( reinterpret_cast<const jchar*> ( value.utf16() ), value.length() );
}

JNIEnv* AttachCurrentThread ( JavaVM* javaVM, bool& didAttach )
{
    didAttach         = false;
    JNIEnv*    env    = nullptr;
    const jint status = javaVM->GetEnv ( reinterpret_cast<void**> ( &env ), JNI_VERSION_1_6 );
    if ( status == JNI_OK )
    {
        return env;
    }
    if ( status != JNI_EDETACHED )
    {
        return nullptr;
    }

#if defined( Q_OS_ANDROID )
    if ( javaVM->AttachCurrentThread ( &env, nullptr ) != JNI_OK )
#else
    if ( javaVM->AttachCurrentThread ( reinterpret_cast<void**> ( &env ), nullptr ) != JNI_OK )
#endif
    {
        return nullptr;
    }
    didAttach = true;
    return env;
}

QString StripChatMarkup ( QString message )
{
    message.remove ( QRegularExpression ( "<[^>]*>" ) );
    message.replace ( "&lt;", "<" );
    message.replace ( "&gt;", ">" );
    message.replace ( "&quot;", "\"" );
    message.replace ( "&#39;", "'" );
    message.replace ( "&amp;", "&" );
    return message;
}
} // namespace

class CComposeEngineController : public QObject
{
    Q_OBJECT

    struct PendingDirectoryRequest
    {
        CHostAddress address;
        QString     key;
        int          attempts;
    };

public:
    CComposeEngineController ( CComposeEngineThread* engineThread, const QString& settingsFile ) :
        QObject(),
        m_engineThread ( engineThread ),
        m_client ( new CClient ( DEFAULT_PORT_NUMBER, DEFAULT_QOS_NUMBER, false, QString(), false, false ) ),
        m_settings ( nullptr ),
        m_timer ( nullptr ),
        m_serverListTimer ( nullptr ),
        m_pingTimer ( nullptr ),
        m_directoryPingTimer ( nullptr ),
        m_serverListRosterTimer ( nullptr ),
        m_serverListDirectoryFailures ( 0 ),
        m_serverListLoading ( false ),
        m_showServerMusicians ( false ),
        m_sendMuted ( false ),
        m_pingMs ( -1 )
    {
        const QFileInfo settingsInfo ( settingsFile );
        QDir().mkpath ( settingsInfo.absolutePath() );

        m_settings = new CClientSettings ( m_client, settingsFile );
        m_settings->Load ( QList<QString>() );
        m_client->SetSettings ( m_settings );
        const int customDirectoryIndex = qBound ( 0, m_settings->iCustomDirectoryIndex, MAX_NUM_SERVER_ADDR_ITEMS - 1 );
        m_directoryAddress = NetworkUtil::GetDirectoryAddress ( m_settings->eDirectoryType, m_settings->vstrDirectoryAddress[customDirectoryIndex] );
        m_gains.fill ( 1.0f, MAX_NUM_CHANNELS );
        m_pans.fill ( 0.0f, MAX_NUM_CHANNELS );
        m_muted.fill ( false, MAX_NUM_CHANNELS );
        m_solo.fill ( false, MAX_NUM_CHANNELS );

        m_pingTimer = new QTimer ( this );
        m_pingTimer->setInterval ( PING_UPDATE_TIME_MS );
        QObject::connect ( m_pingTimer, &QTimer::timeout, this, [this]() {
            if ( m_client->GetConnectionState() == CS_CONNECTED )
            {
                m_client->CreateCLPingMes();
            }
        } );

        QObject::connect ( m_client, &CClient::ConnectionStateChanged, this, [this] ( EConnectionState state ) {
            if ( state == CS_CONNECTED )
            {
                m_pingMs = -1;
                m_pingTimer->start();
                m_client->CreateCLPingMes();
            }
            else
            {
                m_pingTimer->stop();
                m_pingMs = -1;
            }
            PublishSnapshot();
        } );
        QObject::connect ( m_client, &CClient::Connecting, this, [this] ( const QString& ) { PublishSnapshot(); } );
        QObject::connect ( m_client, &CClient::ConnectingFailed, this, [this] ( const QString& error ) {
            m_error = error;
            PublishSnapshot();
        } );
        QObject::connect ( m_client, &CClient::SoundDeviceChanged, this, [this] ( const QString& error ) {
            m_audioError = error;
            PublishSnapshot();
        } );
        QObject::connect ( m_client, &CClient::Disconnected, this, [this]() { PublishSnapshot(); } );
        QObject::connect ( m_client, &CClient::PingTimeReceived, this, [this] ( int ping ) {
            m_pingMs = ping;
            PublishSnapshot();
        } );
        QObject::connect ( m_client, &CClient::ConClientListMesReceived, this, [this] ( const CVector<CChannelInfo>& channels ) {
            m_channels.clear();
            for ( int i = 0; i < channels.Size(); ++i )
            {
                m_channels.append ( channels[i] );
            }
            ResizeChannelState();
            PublishSnapshot();
        } );
        QObject::connect ( m_client, &CClient::CLChannelLevelListReceived, this, [this] ( const CHostAddress&, const CVector<uint16_t>& levels ) {
            m_levels.clear();
            for ( int i = 0; i < levels.Size(); ++i )
            {
                m_levels.append ( levels[i] );
            }
            PublishSnapshot();
        } );
        QObject::connect ( m_client->getConnLessProtocol(),
                           &CProtocol::CLServerListReceived,
                           this,
                           [this] ( const CHostAddress& directory, const CVector<CServerInfo>& servers ) {
                               int pendingIndex = -1;
                               for ( int i = 0; i < m_pendingDirectoryRequests.size(); ++i )
                               {
                                   if ( directory == m_pendingDirectoryRequests[i].address )
                                   {
                                       pendingIndex = i;
                                       break;
                                   }
                               }
                               if ( pendingIndex < 0 )
                               {
                                   return;
                               }
                               const QString directoryKey = m_pendingDirectoryRequests[pendingIndex].key;
                               m_pendingDirectoryRequests.removeAt ( pendingIndex );

                               for ( int i = 0; i < servers.Size(); ++i )
                               {
                                   const QString serverAddress = servers[i].HostAddr.toString();
                                   QStringList&  directoryKeys = m_serverDirectoryKeys[serverAddress];
                                   if ( !directoryKeys.contains ( directoryKey ) )
                                   {
                                       directoryKeys.append ( directoryKey );
                                   }
                                   if ( m_serverAddresses.contains ( serverAddress ) )
                                   {
                                       continue;
                                   }
                                   m_serverAddresses.insert ( serverAddress );
                                   m_servers.append ( servers[i] );
                                   m_serverPingQueue.enqueue ( servers[i].HostAddr );
                               }
                               if ( !m_serverPingQueue.isEmpty() && !m_directoryPingTimer->isActive() )
                               {
                                   m_directoryPingTimer->start();
                               }
                               if ( m_pendingDirectoryRequests.isEmpty() )
                               {
                                   FinishServerListRequest();
                               }
                               PublishSnapshot();
                           } );
        QObject::connect ( m_client,
                           &CClient::CLPingTimeWithNumClientsReceived,
                           this,
                           [this] ( const CHostAddress& address, int pingMs, int musicianCount ) {
                               const QString serverAddress = address.toString();
                               if ( m_serverAddresses.contains ( serverAddress ) )
                               {
                                   m_serverStats.insert ( serverAddress, qMakePair ( pingMs, musicianCount ) );
                                   if ( m_showServerMusicians )
                                   {
                                       RequestServerMusicianList ( address, musicianCount );
                                   }
                                   PublishSnapshot();
                               }
                           } );
        QObject::connect ( m_client,
                           &CClient::CLConnClientsListMesReceived,
                           this,
                           [this] ( const CHostAddress& address, const CVector<CChannelInfo>& channels ) {
                               const QString serverAddress = address.toString();
                               if ( !m_showServerMusicians || !m_serverAddresses.contains ( serverAddress ) )
                               {
                                   return;
                               }
                               m_pendingServerMusicianRequests.remove ( serverAddress );
                               QJsonArray musicians;
                               const int instrumentCount = CInstPictures::GetNumAvailableInst();
                               for ( int i = 0; i < channels.Size(); ++i )
                               {
                                   const CChannelInfo& channel = channels[i];
                                   const int instrumentId = channel.iInstrument >= 0 && channel.iInstrument < instrumentCount
                                                                ? channel.iInstrument
                                                                : 0;
                                   const int countryId = channel.eCountry == QLocale::AnyCountry ||
                                                                 CLocale::IsCountryCodeSupported ( channel.eCountry )
                                                             ? static_cast<int> ( channel.eCountry )
                                                             : static_cast<int> ( QLocale::AnyCountry );
                                   musicians.append ( QJsonObject{
                                       { "name", channel.strName.left ( MAX_LEN_FADER_TAG ) },
                                       { "instrumentId", instrumentId },
                                       { "countryId", countryId },
                                   } );
                               }
                               m_serverMusicians.insert ( serverAddress, musicians );
                               m_serverMusicianFetchedAt.insert ( serverAddress, QDateTime::currentMSecsSinceEpoch() );
                               PublishSnapshot();
                           } );
        QObject::connect ( m_client, &CClient::ChatTextReceived, this, [this] ( const QString& text ) {
            m_chat.append ( QJsonObject{ { "sender", QString() }, { "text", StripChatMarkup ( text ) }, { "timestamp", QString() } } );
            while ( m_chat.size() > 100 )
            {
                m_chat.removeFirst();
            }
            PublishSnapshot();
        } );

        m_timer = new QTimer ( this );
        m_timer->setInterval ( 100 );
        QObject::connect ( m_timer, &QTimer::timeout, this, &CComposeEngineController::PublishSnapshot );
        m_timer->start();
        m_serverListTimer = new QTimer ( this );
        m_serverListTimer->setInterval ( 2000 );
        QObject::connect ( m_serverListTimer, &QTimer::timeout, this, [this]() { RetryServerListRequest(); } );
        m_directoryPingTimer = new QTimer ( this );
        m_directoryPingTimer->setInterval ( 11 );
        QObject::connect ( m_directoryPingTimer, &QTimer::timeout, this, [this]() {
            if ( m_serverPingQueue.isEmpty() )
            {
                m_directoryPingTimer->stop();
                return;
            }
            m_client->CreateCLServerListPingMes ( m_serverPingQueue.dequeue() );
        } );
        m_serverListRosterTimer = new QTimer ( this );
        m_serverListRosterTimer->setInterval ( PING_UPDATE_TIME_SERVER_LIST_MS );
        QObject::connect ( m_serverListRosterTimer, &QTimer::timeout, this, [this]() { QueueServerListPings(); } );
        PublishSnapshot();
    }

    ~CComposeEngineController() override
    {
        if ( m_client->GetConnectionState() != CS_DISCONNECTED )
        {
            m_client->Disconnect();
        }
        m_settings->Save ( true );
        delete m_client;
        delete m_settings;
    }

public slots:
    void ConnectToServer ( const QString& address )
    {
        m_error.clear();
        SetClientSendMuted ( true );
        m_client->Connect ( address, address );
        PublishSnapshot();
    }

    void DisconnectFromServer()
    {
        m_error.clear();
        m_client->Disconnect();
        PublishSnapshot();
    }

    void SetShowServerMusicians ( bool enabled )
    {
        m_showServerMusicians = enabled;
        if ( enabled )
        {
            m_serverListRosterTimer->start();
            QueueServerListPings();
            for ( const CServerInfo& server : m_servers )
            {
                const QString serverAddress = server.HostAddr.toString();
                const auto stats = m_serverStats.constFind ( serverAddress );
                if ( stats != m_serverStats.cend() )
                {
                    RequestServerMusicianList ( server.HostAddr, stats->second );
                }
            }
        }
        else
        {
            m_serverListRosterTimer->stop();
            m_pendingServerMusicianRequests.clear();
            m_serverMusicianFetchedAt.clear();
            m_serverMusicians.clear();
        }
        PublishSnapshot();
    }

    void PollServerList ( const QString& directory )
    {
        QVector<QPair<QString, QString>> directoryEntries;
        const bool  allDirectories = directory.trimmed().isEmpty();
        if ( allDirectories )
        {
            for ( int type = AT_DEFAULT; type < AT_CUSTOM; ++type )
            {
                directoryEntries.append ( qMakePair ( QString::number ( type ),
                                                      NetworkUtil::GetDirectoryAddress ( static_cast<EDirectoryType> ( type ), QString() ) ) );
            }
            for ( int index = 0; index < m_settings->vstrDirectoryAddress.Size(); ++index )
            {
                if ( !m_settings->vstrDirectoryAddress[index].isEmpty() )
                {
                    directoryEntries.append ( qMakePair ( QString ( "custom:%1" ).arg ( index ), m_settings->vstrDirectoryAddress[index] ) );
                }
            }
        }
        else
        {
            directoryEntries.append ( qMakePair ( QString ( "custom:%1" ).arg ( directory ), directory ) );
        }

        if ( !allDirectories )
        {
            const QString selectedAddress = NetworkUtil::FixAddress ( directoryEntries.first().second );
            CHostAddress  parsedDirectoryAddress;
            if ( !NetworkUtil::ParseNetworkAddress ( selectedAddress, parsedDirectoryAddress, false ) )
            {
                m_serverListLoading = false;
                m_serverListError   = QStringLiteral ( "Ungültige Verzeichnisadresse." );
                m_serverListTimer->stop();
                PublishSnapshot();
                return;
            }
            RememberDirectory ( selectedAddress );
        }

        m_servers.clear();
        m_serverStats.clear();
        m_serverAddresses.clear();
        m_serverDirectoryKeys.clear();
        m_serverMusicians.clear();
        m_pendingServerMusicianRequests.clear();
        m_serverMusicianFetchedAt.clear();
        m_serverPingQueue.clear();
        m_directoryPingTimer->stop();
        m_pendingDirectoryRequests.clear();
        m_serverListDirectoryFailures = 0;
        QSet<QString> seenDirectoryAddresses;
        for ( const QPair<QString, QString>& entry : directoryEntries )
        {
            const QString selectedAddress = NetworkUtil::FixAddress ( entry.second );
            if ( selectedAddress.isEmpty() || seenDirectoryAddresses.contains ( selectedAddress.toLower() ) )
            {
                continue;
            }
            seenDirectoryAddresses.insert ( selectedAddress.toLower() );

            CHostAddress parsedDirectoryAddress;
            if ( !NetworkUtil::ParseNetworkAddress ( selectedAddress, parsedDirectoryAddress, false ) )
            {
                ++m_serverListDirectoryFailures;
                continue;
            }
            const QString directoryHost = parsedDirectoryAddress.toString().toLower();
            if ( seenDirectoryAddresses.contains ( directoryHost ) )
            {
                continue;
            }
            seenDirectoryAddresses.insert ( directoryHost );
            m_pendingDirectoryRequests.append ( PendingDirectoryRequest{ parsedDirectoryAddress, entry.first, 0 } );
        }

        m_serverListLoading = !m_pendingDirectoryRequests.isEmpty();
        m_serverListError.clear();
        if ( m_pendingDirectoryRequests.isEmpty() )
        {
            FinishServerListRequest();
        }
        else
        {
            SendServerListRequests();
        }
        PublishSnapshot();
    }

    void SetClientSendMuted ( bool muted )
    {
        m_sendMuted = muted;
        m_client->SetMuteOutStream ( muted );
        PublishSnapshot();
    }

    void SetGain ( int channelId, float gain )
    {
        if ( !IsActiveChannel ( channelId ) )
        {
            return;
        }
        m_gains[channelId] = qBound ( 0.0f, gain, 1.0f );
        ApplyChannelGain ( channelId );
        PublishSnapshot();
    }

    void SetPan ( int channelId, float pan )
    {
        if ( !IsActiveChannel ( channelId ) )
        {
            return;
        }
        m_pans[channelId] = qBound ( -1.0f, pan, 1.0f );
        m_client->SetRemoteChanPan ( channelId, ( m_pans[channelId] + 1.0f ) / 2.0f );
        PublishSnapshot();
    }

    void SetMuted ( int channelId, bool muted )
    {
        if ( !IsActiveChannel ( channelId ) )
        {
            return;
        }
        m_muted[channelId] = muted;
        ApplyAllChannelGains();
        PublishSnapshot();
    }

    void SetSolo ( int channelId, bool solo )
    {
        if ( !IsActiveChannel ( channelId ) )
        {
            return;
        }
        m_solo[channelId] = solo;
        ApplyAllChannelGains();
        PublishSnapshot();
    }

    void SendChatText ( const QString& message )
    {
        const QString cleanMessage = message.trimmed();
        if ( cleanMessage.isEmpty() || cleanMessage.toUtf8().size() > MAX_LEN_CHAT_TEXT || !m_client->IsConnected() )
        {
            return;
        }
        m_client->CreateChatTextMes ( cleanMessage );
    }

    void SetMusicianProfile ( const QString& alias, int instrumentId, int countryId, const QString& city, int skillId )
    {
        const int instrumentCount = CInstPictures::GetNumAvailableInst();
        if ( instrumentCount > 0 )
        {
            m_client->ChannelInfo.iInstrument = qBound ( 0, instrumentId, instrumentCount - 1 );
        }
        m_client->ChannelInfo.strName = alias.left ( MAX_LEN_FADER_TAG );
        m_client->ChannelInfo.strCity = city.left ( MAX_LEN_SERVER_CITY );

        const QLocale::Country country = static_cast<QLocale::Country> ( countryId );
        m_client->ChannelInfo.eCountry =
            ( country == QLocale::AnyCountry || CLocale::IsCountryCodeSupported ( countryId ) ) ? country : QLocale::AnyCountry;
        m_client->ChannelInfo.eSkillLevel = static_cast<ESkillLevel> ( qBound ( 0, skillId, 3 ) );
        m_client->SetRemoteInfo();
        m_settings->Save ( false );
        PublishSnapshot();
    }

    void SetAudioDevice ( const QString& device )
    {
        if ( !m_client->GetSndCrdDevNames().contains ( device ) )
        {
            m_audioError = QStringLiteral ( "Das ausgewählte Audiogerät ist nicht mehr verfügbar." );
        }
        else
        {
            m_audioError = m_client->SetSndCrdDev ( device );
            m_settings->Save ( false );
        }
        PublishSnapshot();
    }

    void SetAudioFormat ( int format )
    {
        if ( format < AQ_LOW || format > AQ_RAW )
            return;
        m_client->SetAudioQuality ( static_cast<EAudioQuality> ( format ) );
        m_settings->Save ( false );
        PublishSnapshot();
    }

    void SetAudioChannels ( int channels )
    {
        if ( channels < CC_MONO || channels > CC_STEREO )
            return;
        m_client->SetAudioChannels ( static_cast<EAudChanConf> ( channels ) );
        m_settings->Save ( false );
        PublishSnapshot();
    }

    void SetSmallNetworkBuffers ( bool enabled )
    {
        m_client->SetEnableOPUS64 ( enabled );
        m_settings->Save ( false );
        PublishSnapshot();
    }

    void SetAudioBufferFactor ( int factor )
    {
        if ( factor != FRAME_SIZE_FACTOR_PREFERRED && factor != FRAME_SIZE_FACTOR_DEFAULT && factor != FRAME_SIZE_FACTOR_SAFE )
            return;
        m_client->SetSndCrdPrefFrameSizeFactor ( factor );
        m_settings->Save ( false );
        PublishSnapshot();
    }

    void SetAutomaticJitterBuffer ( bool enabled )
    {
        m_client->SetDoAutoSockBufSize ( enabled );
        m_settings->Save ( false );
        PublishSnapshot();
    }

    void SetJitterBufferFrames ( int frames )
    {
        if ( m_client->GetDoAutoSockBufSize() || frames < MIN_NET_BUF_SIZE_NUM_BL || frames > MAX_NET_BUF_SIZE_NUM_BL )
            return;
        m_client->SetSockBufNumFrames ( frames );
        m_settings->Save ( false );
        PublishSnapshot();
    }

    void SetServerJitterBufferFrames ( int frames )
    {
        if ( m_client->GetDoAutoSockBufSize() || frames < MIN_NET_BUF_SIZE_NUM_BL || frames > MAX_NET_BUF_SIZE_NUM_BL )
            return;
        m_client->SetServerSockBufNumFrames ( frames );
        m_settings->Save ( false );
        PublishSnapshot();
    }

    void StopEngine()
    {
        if ( m_client->GetConnectionState() != CS_DISCONNECTED )
        {
            m_client->Disconnect();
        }
        m_engineThread->quit();
    }

private:
    void RememberDirectory ( const QString& address )
    {
        m_directoryAddress = NetworkUtil::FixAddress ( address );

        for ( int type = AT_DEFAULT; type < AT_CUSTOM; ++type )
        {
            const EDirectoryType directoryType = static_cast<EDirectoryType> ( type );
            if ( NetworkUtil::GetDirectoryAddress ( directoryType, QString() ).compare ( m_directoryAddress, Qt::CaseInsensitive ) == 0 )
            {
                m_settings->eDirectoryType        = directoryType;
                m_settings->iCustomDirectoryIndex = 0;
                return;
            }
        }

        for ( int index = 0; index < m_settings->vstrDirectoryAddress.Size(); ++index )
        {
            if ( m_settings->vstrDirectoryAddress[index].compare ( m_directoryAddress, Qt::CaseInsensitive ) == 0 )
            {
                m_settings->eDirectoryType        = AT_CUSTOM;
                m_settings->iCustomDirectoryIndex = index;
                return;
            }
        }

        m_settings->vstrDirectoryAddress.StringFiFoWithCompare ( m_directoryAddress );
        m_settings->eDirectoryType        = AT_CUSTOM;
        m_settings->iCustomDirectoryIndex = 0;
    }

    void SendServerListRequests()
    {
        for ( PendingDirectoryRequest& request : m_pendingDirectoryRequests )
        {
            m_client->CreateCLReqServerListMes ( request.address );
            ++request.attempts;
        }
        m_serverListTimer->start();
    }

    void RetryServerListRequest()
    {
        if ( !m_serverListLoading )
        {
            m_serverListTimer->stop();
            return;
        }

        for ( int i = m_pendingDirectoryRequests.size() - 1; i >= 0; --i )
        {
            if ( m_pendingDirectoryRequests[i].attempts >= 3 )
            {
                m_pendingDirectoryRequests.removeAt ( i );
                ++m_serverListDirectoryFailures;
            }
        }
        if ( m_pendingDirectoryRequests.isEmpty() )
        {
            FinishServerListRequest();
            PublishSnapshot();
            return;
        }

        SendServerListRequests();
    }

    void FinishServerListRequest()
    {
        m_serverListLoading = false;
        if ( m_serverListDirectoryFailures > 0 )
        {
            m_serverListError = m_serverListDirectoryFailures == 1
                                    ? QStringLiteral ( "Ein Verzeichnis konnte nicht abgefragt werden." )
                                    : QStringLiteral ( "%1 Verzeichnisse konnten nicht abgefragt werden." )
                                          .arg ( m_serverListDirectoryFailures );
        }
        else
        {
            m_serverListError.clear();
        }
        m_serverListTimer->stop();
    }

    void QueueServerListPings()
    {
        if ( !m_showServerMusicians || !m_serverPingQueue.isEmpty() || m_directoryPingTimer->isActive() )
        {
            return;
        }
        for ( const CServerInfo& server : m_servers )
        {
            m_serverPingQueue.enqueue ( server.HostAddr );
        }
        if ( !m_serverPingQueue.isEmpty() )
        {
            m_directoryPingTimer->start();
        }
    }

    void RequestServerMusicianList ( const CHostAddress& address, int musicianCount )
    {
        const QString serverAddress = address.toString();
        if ( !m_showServerMusicians || !m_serverAddresses.contains ( serverAddress ) )
        {
            return;
        }
        if ( musicianCount <= 0 )
        {
            m_serverMusicians.insert ( serverAddress, QJsonArray() );
            m_pendingServerMusicianRequests.remove ( serverAddress );
            return;
        }
        const qint64 now = QDateTime::currentMSecsSinceEpoch();
        if ( m_serverMusicians.contains ( serverAddress ) &&
             m_serverMusicians.value ( serverAddress ).size() == musicianCount &&
             now - m_serverMusicianFetchedAt.value ( serverAddress, 0 ) < 10000 )
        {
            return;
        }

        const auto pendingRequest = m_pendingServerMusicianRequests.constFind ( serverAddress );
        if ( pendingRequest != m_pendingServerMusicianRequests.cend() && now - pendingRequest.value() < 10000 )
        {
            return;
        }
        m_pendingServerMusicianRequests.insert ( serverAddress, now );
        m_client->CreateCLServerListReqConnClientsListMes ( address );
    }

    void ResizeChannelState()
    {
        QVector<bool> activeChannels ( MAX_NUM_CHANNELS, false );
        for ( const CChannelInfo& channel : m_channels )
        {
            if ( channel.iChanID >= 0 && channel.iChanID < MAX_NUM_CHANNELS )
            {
                activeChannels[channel.iChanID] = true;
            }
        }
        for ( int i = 0; i < MAX_NUM_CHANNELS; ++i )
        {
            if ( !activeChannels[i] )
            {
                m_gains[i] = 1.0f;
                m_pans[i]  = 0.0f;
                m_muted[i] = false;
                m_solo[i]  = false;
            }
        }
    }

    bool IsActiveChannel ( int channelId ) const
    {
        if ( !m_client->IsConnected() || channelId < 0 || channelId >= MAX_NUM_CHANNELS )
        {
            return false;
        }
        for ( const CChannelInfo& channel : m_channels )
        {
            if ( channel.iChanID == channelId )
            {
                return true;
            }
        }
        return false;
    }

    void ApplyChannelGain ( int channelId )
    {
        bool anySolo = false;
        for ( const CChannelInfo& channel : m_channels )
        {
            anySolo = anySolo || m_solo[channel.iChanID];
        }
        const bool  mutedBySolo = anySolo && !m_solo[channelId];
        const float gain        = ( m_muted[channelId] || mutedBySolo ) ? 0.0f : m_gains[channelId];
        m_client->SetRemoteChanGain ( channelId, gain, channelId == 0 );
    }

    void ApplyAllChannelGains()
    {
        for ( const CChannelInfo& channel : m_channels )
        {
            ApplyChannelGain ( channel.iChanID );
        }
    }

    void PublishSnapshot()
    {
        QJsonArray directories;
        for ( int type = AT_DEFAULT; type < AT_CUSTOM; ++type )
        {
            const EDirectoryType directoryType = static_cast<EDirectoryType> ( type );
            directories.append ( QJsonObject{
                { "key", QString::number ( type ) },
                { "name", DirectoryTypeToString ( directoryType ) },
                { "address", NetworkUtil::GetDirectoryAddress ( directoryType, QString() ) },
            } );
        }
        for ( int index = 0; index < m_settings->vstrDirectoryAddress.Size(); ++index )
        {
            const QString address = m_settings->vstrDirectoryAddress[index];
            if ( !address.isEmpty() )
            {
                directories.append ( QJsonObject{
                    { "key", QString ( "custom:%1" ).arg ( index ) },
                    { "name", address },
                    { "address", address },
                } );
            }
        }

        QJsonArray instruments;
        for ( int instrumentId = 0; instrumentId < CInstPictures::GetNumAvailableInst(); ++instrumentId )
        {
            instruments.append ( QJsonObject{
                { "id", instrumentId },
                { "label", CInstPictures::GetName ( instrumentId ) },
                { "icon", CInstPictures::GetResourceReference ( instrumentId ).section ( "/res/", 1 ) },
            } );
        }

        QJsonArray countries;
        for ( int countryId = static_cast<int> ( QLocale::AnyCountry ); countryId < static_cast<int> ( QLocale::LastCountry ); ++countryId )
        {
            if ( countryId != static_cast<int> ( QLocale::AnyCountry ) && !CLocale::IsCountryCodeSupported ( countryId ) )
            {
                continue;
            }
            const QLocale::Country country = static_cast<QLocale::Country> ( countryId );
            const QString flagResource = CLocale::GetCountryFlagIconsResourceReference ( country );
            countries.append ( QJsonObject{
                { "id", countryId },
                { "label", country == QLocale::AnyCountry ? QStringLiteral ( "None" ) : QLocale::countryToString ( country ) },
                { "icon", flagResource.section ( "/res/", 1 ) },
            } );
        }

        const CChannelCoreInfo& profile     = m_client->ChannelInfo;
        const QStringList       skillLabels = { QStringLiteral ( "None" ),
                                                QStringLiteral ( "Beginner" ),
                                                QStringLiteral ( "Intermediate" ),
                                                QStringLiteral ( "Expert" ) };
        const int               skillId     = qBound ( 0, static_cast<int> ( profile.eSkillLevel ), skillLabels.size() - 1 );
        const QJsonObject       profileJson{
                  { "alias", profile.strName },
                  { "instrument", CInstPictures::GetName ( profile.iInstrument ) },
                  { "instrumentId", profile.iInstrument },
                  { "country", profile.eCountry == QLocale::AnyCountry ? QStringLiteral ( "None" ) : QLocale::countryToString ( profile.eCountry ) },
                  { "countryId", static_cast<int> ( profile.eCountry ) },
                  { "city", profile.strCity },
                  { "skill", skillLabels[skillId] },
                  { "skillId", skillId },
        };

        const QStringList audioDeviceNames = m_client->GetSndCrdDevNames();
        QJsonArray        audioDevices;
        for ( const QString& device : audioDeviceNames )
        {
            audioDevices.append ( device );
        }
        QJsonArray supportedBufferFactors;
        if ( m_client->GetFraSiFactPrefSupported() )
            supportedBufferFactors.append ( FRAME_SIZE_FACTOR_PREFERRED );
        if ( m_client->GetFraSiFactDefSupported() )
            supportedBufferFactors.append ( FRAME_SIZE_FACTOR_DEFAULT );
        if ( m_client->GetFraSiFactSafeSupported() )
            supportedBufferFactors.append ( FRAME_SIZE_FACTOR_SAFE );
        const QJsonObject audioJson{
            { "devices", audioDevices },
            { "device", m_client->GetSndCrdDev() },
            { "format", static_cast<int> ( m_client->GetAudioQuality() ) },
            { "channels", static_cast<int> ( m_client->GetAudioChannels() ) },
            { "smallNetworkBuffers", m_client->GetEnableOPUS64() },
            { "bufferFactor", m_client->GetSndCrdPrefFrameSizeFactor() },
            { "supportedBufferFactors", supportedBufferFactors },
            { "actualBufferSamples", m_client->GetSndCrdActualMonoBlSize() },
            { "automaticJitterBuffer", m_client->GetDoAutoSockBufSize() },
            { "jitterBufferFrames", m_client->GetSockBufNumFrames() },
            { "serverJitterBufferFrames", m_client->GetServerSockBufNumFrames() },
            { "audioError", m_audioError },
        };

        QJsonArray servers;
        for ( const CServerInfo& server : m_servers )
        {
            const QString         serverAddress = server.HostAddr.toString();
            const QPair<int, int> stats         = m_serverStats.value ( serverAddress, qMakePair ( -1, 0 ) );
            QJsonArray directoryKeys;
            for ( const QString& directoryKey : m_serverDirectoryKeys.value ( serverAddress ) )
            {
                directoryKeys.append ( directoryKey );
            }
            const QJsonArray musicians = m_serverMusicians.value ( serverAddress );
            QStringList locationParts;
            if ( !server.strCity.isEmpty() )
            {
                locationParts.append ( server.strCity );
            }
            if ( server.eCountry != QLocale::AnyCountry )
            {
                locationParts.append ( QLocale::countryToString ( server.eCountry ) );
            }
            servers.append ( QJsonObject{
                { "address", server.HostAddr.toString() },
                { "name", server.strName },
                { "location", locationParts.join ( ", " ) },
                { "genre", QString() },
                { "musicianCount", stats.second },
                { "pingMs", stats.first < 0 ? QJsonValue::Null : QJsonValue ( stats.first ) },
                { "directoryKeys", directoryKeys },
                { "musicians", musicians },
            } );
        }

        QJsonArray channels;
        for ( int i = 0; i < m_channels.size(); ++i )
        {
            const CChannelInfo& channel = m_channels[i];
            const float         level   = i < m_levels.size() ? m_levels[i] / 9.0f : 0.0f;
            channels.append ( QJsonObject{
                { "id", channel.iChanID },
                { "name", channel.strName },
                { "instrument", CInstPictures::GetName ( channel.iInstrument ) },
                { "instrumentId", channel.iInstrument },
                { "countryId", static_cast<int> ( channel.eCountry ) },
                { "level", level },
                { "gain", m_gains.value ( channel.iChanID, 1.0f ) },
                { "pan", m_pans.value ( channel.iChanID, 0.0f ) },
                { "muted", m_muted.value ( channel.iChanID, false ) },
                { "solo", m_solo.value ( channel.iChanID, false ) },
            } );
        }

        QString status = QStringLiteral ( "DISCONNECTED" );
        switch ( m_client->GetConnectionState() )
        {
        case CS_CONNECTING:
            status = QStringLiteral ( "CONNECTING" );
            break;
        case CS_CONNECTED:
            status = QStringLiteral ( "CONNECTED" );
            break;
        case CS_DISCONNECTED:
            status = QStringLiteral ( "DISCONNECTED" );
            break;
        }

        const double left       = m_client->GetLevelForMeterdBLeft();
        const double right      = m_client->GetLevelForMeterdBRight();
        const float  leftMeter  = qBound ( 0.0, left / NUM_STEPS_LED_BAR, 1.0 );
        const float  rightMeter = qBound ( 0.0, right / NUM_STEPS_LED_BAR, 1.0 );
        QJsonObject  snapshot{
             { "status", status },
             { "detail", m_error },
             { "serverAddress", m_client->GetConnectedServerName() },
             { "directoryAddress", m_directoryAddress },
             { "directories", directories },
             { "profile", profileJson },
             { "instruments", instruments },
             { "countries", countries },
             { "audio", audioJson },
             { "serverListLoading", m_serverListLoading },
             { "serverListError", m_serverListError },
             { "pingMs", m_pingMs < 0 ? QJsonValue::Null : QJsonValue ( m_pingMs ) },
             { "delayMs", m_pingMs < 0 ? QJsonValue::Null : QJsonValue ( m_client->EstimatedOverallDelay ( m_pingMs ) ) },
             { "inputLevel", leftMeter },
             { "inputLevelRight", rightMeter },
             { "sendMuted", m_sendMuted },
             { "servers", servers },
             { "channels", channels },
             { "chat", m_chat },
        };
        const QByteArray payload = QJsonDocument ( snapshot ).toJson ( QJsonDocument::Compact );

        m_engineThread->NotifySnapshot ( QString::fromUtf8 ( payload ) );
    }

    CComposeEngineThread*           m_engineThread;
    CClient*                        m_client;
    CClientSettings*                m_settings;
    QTimer*                         m_timer;
    QTimer*                         m_serverListTimer;
    QTimer*                         m_pingTimer;
    QTimer*                         m_directoryPingTimer;
    QTimer*                         m_serverListRosterTimer;
    QVector<CServerInfo>            m_servers;
    QVector<PendingDirectoryRequest> m_pendingDirectoryRequests;
    QVector<CChannelInfo>           m_channels;
    QVector<uint16_t>               m_levels;
    QHash<QString, QPair<int, int>> m_serverStats;
    QSet<QString>                   m_serverAddresses;
    QHash<QString, QStringList>      m_serverDirectoryKeys;
    QHash<QString, QJsonArray>       m_serverMusicians;
    QHash<QString, qint64>           m_pendingServerMusicianRequests;
    QHash<QString, qint64>           m_serverMusicianFetchedAt;
    QQueue<CHostAddress>            m_serverPingQueue;
    QVector<float>                  m_gains;
    QVector<float>                  m_pans;
    QVector<bool>                   m_muted;
    QVector<bool>                   m_solo;
    QJsonArray                      m_chat;
    QString                         m_error;
    QString                         m_audioError;
    QString                         m_directoryAddress;
    QString                         m_serverListError;
    int                             m_serverListDirectoryFailures;
    bool                            m_serverListLoading;
    bool                            m_showServerMusicians;
    bool                            m_sendMuted;
    int                             m_pingMs;
};
CComposeEngineThread::CComposeEngineThread ( JavaVM*        javaVM,
                                             jobject        listener,
                                             jclass         listenerClass,
                                             jmethodID      invokeMethod,
                                             const QString& settingsFile ) :
    QThread(),
    m_javaVM ( javaVM ),
    m_listener ( listener ),
    m_listenerClass ( listenerClass ),
    m_invokeMethod ( invokeMethod ),
    m_settingsFile ( settingsFile ),
    m_controllerMutex(),
    m_controller ( nullptr ),
    m_ready ( false )
{}

CComposeEngineThread::~CComposeEngineThread() { StopAndWait(); }

bool CComposeEngineThread::IsReady() const
{
    QMutexLocker locker ( &m_controllerMutex );
    return m_ready;
}

void CComposeEngineThread::NotifySnapshot ( const QString& payload )
{
    bool    didAttach = false;
    JNIEnv* env       = AttachCurrentThread ( m_javaVM, didAttach );
    if ( env == nullptr )
    {
        return;
    }

    jstring javaPayload = ToJavaString ( env, payload );
    jobject result      = env->CallObjectMethod ( m_listener, m_invokeMethod, javaPayload );
    if ( env->ExceptionCheck() )
    {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
    if ( result != nullptr )
    {
        env->DeleteLocalRef ( result );
    }
    env->DeleteLocalRef ( javaPayload );

    if ( didAttach )
    {
        m_javaVM->DetachCurrentThread();
    }
}

void CComposeEngineThread::ReleaseJavaRefs ( JNIEnv* env )
{
    if ( m_listener != nullptr )
    {
        env->DeleteGlobalRef ( m_listener );
        m_listener = nullptr;
    }
    if ( m_listenerClass != nullptr )
    {
        env->DeleteGlobalRef ( m_listenerClass );
        m_listenerClass = nullptr;
    }
}

void CComposeEngineThread::StopAndWait()
{
    QPointer<CComposeEngineController> controller;
    {
        QMutexLocker locker ( &m_controllerMutex );
        controller = m_controller;
    }
    if ( controller != nullptr )
    {
        QMetaObject::invokeMethod ( controller, "StopEngine", Qt::QueuedConnection );
    }
    wait();
}

void CComposeEngineThread::Connect ( const QString& address )
{
    QPointer<CComposeEngineController> controller;
    {
        QMutexLocker locker ( &m_controllerMutex );
        controller = m_controller;
    }
    if ( controller != nullptr )
    {
        QMetaObject::invokeMethod ( controller, "ConnectToServer", Qt::QueuedConnection, Q_ARG ( QString, address ) );
    }
}

void CComposeEngineThread::Disconnect()
{
    QPointer<CComposeEngineController> controller;
    {
        QMutexLocker locker ( &m_controllerMutex );
        controller = m_controller;
    }
    if ( controller != nullptr )
    {
        QMetaObject::invokeMethod ( controller, "DisconnectFromServer", Qt::QueuedConnection );
    }
}

void CComposeEngineThread::RefreshServerList ( const QString& directory )
{
    QPointer<CComposeEngineController> controller;
    {
        QMutexLocker locker ( &m_controllerMutex );
        controller = m_controller;
    }
    if ( controller != nullptr )
    {
        QMetaObject::invokeMethod ( controller, "PollServerList", Qt::QueuedConnection, Q_ARG ( QString, directory ) );
    }
}

void CComposeEngineThread::SetShowServerMusicians ( bool enabled )
{
    QPointer<CComposeEngineController> controller;
    {
        QMutexLocker locker ( &m_controllerMutex );
        controller = m_controller;
    }
    if ( controller != nullptr )
    {
        QMetaObject::invokeMethod ( controller, "SetShowServerMusicians", Qt::QueuedConnection, Q_ARG ( bool, enabled ) );
    }
}

void CComposeEngineThread::SetSendMuted ( bool muted )
{
    QPointer<CComposeEngineController> controller;
    {
        QMutexLocker locker ( &m_controllerMutex );
        controller = m_controller;
    }
    if ( controller != nullptr )
    {
        QMetaObject::invokeMethod ( controller, "SetClientSendMuted", Qt::QueuedConnection, Q_ARG ( bool, muted ) );
    }
}

void CComposeEngineThread::SetChannelGain ( int channelId, float gain )
{
    QPointer<CComposeEngineController> controller;
    {
        QMutexLocker locker ( &m_controllerMutex );
        controller = m_controller;
    }
    if ( controller != nullptr )
    {
        QMetaObject::invokeMethod ( controller, "SetGain", Qt::QueuedConnection, Q_ARG ( int, channelId ), Q_ARG ( float, gain ) );
    }
}

void CComposeEngineThread::SetChannelPan ( int channelId, float pan )
{
    QPointer<CComposeEngineController> controller;
    {
        QMutexLocker locker ( &m_controllerMutex );
        controller = m_controller;
    }
    if ( controller != nullptr )
    {
        QMetaObject::invokeMethod ( controller, "SetPan", Qt::QueuedConnection, Q_ARG ( int, channelId ), Q_ARG ( float, pan ) );
    }
}

void CComposeEngineThread::SetChannelMuted ( int channelId, bool muted )
{
    QPointer<CComposeEngineController> controller;
    {
        QMutexLocker locker ( &m_controllerMutex );
        controller = m_controller;
    }
    if ( controller != nullptr )
    {
        QMetaObject::invokeMethod ( controller, "SetMuted", Qt::QueuedConnection, Q_ARG ( int, channelId ), Q_ARG ( bool, muted ) );
    }
}

void CComposeEngineThread::SetChannelSolo ( int channelId, bool solo )
{
    QPointer<CComposeEngineController> controller;
    {
        QMutexLocker locker ( &m_controllerMutex );
        controller = m_controller;
    }
    if ( controller != nullptr )
    {
        QMetaObject::invokeMethod ( controller, "SetSolo", Qt::QueuedConnection, Q_ARG ( int, channelId ), Q_ARG ( bool, solo ) );
    }
}

void CComposeEngineThread::SendChat ( const QString& message )
{
    QPointer<CComposeEngineController> controller;
    {
        QMutexLocker locker ( &m_controllerMutex );
        controller = m_controller;
    }
    if ( controller != nullptr )
    {
        QMetaObject::invokeMethod ( controller, "SendChatText", Qt::QueuedConnection, Q_ARG ( QString, message ) );
    }
}

void CComposeEngineThread::SetClientProfile ( const QString& alias, int instrumentId, int countryId, const QString& city, int skillId )
{
    QPointer<CComposeEngineController> controller;
    {
        QMutexLocker locker ( &m_controllerMutex );
        controller = m_controller;
    }
    if ( controller != nullptr )
    {
        QMetaObject::invokeMethod ( controller,
                                    "SetMusicianProfile",
                                    Qt::QueuedConnection,
                                    Q_ARG ( QString, alias ),
                                    Q_ARG ( int, instrumentId ),
                                    Q_ARG ( int, countryId ),
                                    Q_ARG ( QString, city ),
                                    Q_ARG ( int, skillId ) );
    }
}

void CComposeEngineThread::SetAudioDevice ( const QString& device )
{
    QPointer<CComposeEngineController> controller;
    {
        QMutexLocker locker ( &m_controllerMutex );
        controller = m_controller;
    }
    if ( controller != nullptr )
        QMetaObject::invokeMethod ( controller, "SetAudioDevice", Qt::QueuedConnection, Q_ARG ( QString, device ) );
}

void CComposeEngineThread::SetAudioFormat ( int format )
{
    QPointer<CComposeEngineController> controller;
    {
        QMutexLocker locker ( &m_controllerMutex );
        controller = m_controller;
    }
    if ( controller != nullptr )
        QMetaObject::invokeMethod ( controller, "SetAudioFormat", Qt::QueuedConnection, Q_ARG ( int, format ) );
}

void CComposeEngineThread::SetAudioChannels ( int channels )
{
    QPointer<CComposeEngineController> controller;
    {
        QMutexLocker locker ( &m_controllerMutex );
        controller = m_controller;
    }
    if ( controller != nullptr )
        QMetaObject::invokeMethod ( controller, "SetAudioChannels", Qt::QueuedConnection, Q_ARG ( int, channels ) );
}

void CComposeEngineThread::SetSmallNetworkBuffers ( bool enabled )
{
    QPointer<CComposeEngineController> controller;
    {
        QMutexLocker locker ( &m_controllerMutex );
        controller = m_controller;
    }
    if ( controller != nullptr )
        QMetaObject::invokeMethod ( controller, "SetSmallNetworkBuffers", Qt::QueuedConnection, Q_ARG ( bool, enabled ) );
}

void CComposeEngineThread::SetAudioBufferFactor ( int factor )
{
    QPointer<CComposeEngineController> controller;
    {
        QMutexLocker locker ( &m_controllerMutex );
        controller = m_controller;
    }
    if ( controller != nullptr )
        QMetaObject::invokeMethod ( controller, "SetAudioBufferFactor", Qt::QueuedConnection, Q_ARG ( int, factor ) );
}

void CComposeEngineThread::SetAutomaticJitterBuffer ( bool enabled )
{
    QPointer<CComposeEngineController> controller;
    {
        QMutexLocker locker ( &m_controllerMutex );
        controller = m_controller;
    }
    if ( controller != nullptr )
        QMetaObject::invokeMethod ( controller, "SetAutomaticJitterBuffer", Qt::QueuedConnection, Q_ARG ( bool, enabled ) );
}

void CComposeEngineThread::SetJitterBufferFrames ( int frames )
{
    QPointer<CComposeEngineController> controller;
    {
        QMutexLocker locker ( &m_controllerMutex );
        controller = m_controller;
    }
    if ( controller != nullptr )
        QMetaObject::invokeMethod ( controller, "SetJitterBufferFrames", Qt::QueuedConnection, Q_ARG ( int, frames ) );
}

void CComposeEngineThread::SetServerJitterBufferFrames ( int frames )
{
    QPointer<CComposeEngineController> controller;
    {
        QMutexLocker locker ( &m_controllerMutex );
        controller = m_controller;
    }
    if ( controller != nullptr )
        QMetaObject::invokeMethod ( controller, "SetServerJitterBufferFrames", Qt::QueuedConnection, Q_ARG ( int, frames ) );
}

void CComposeEngineThread::run()
{
    {
        CComposeEngineController controller ( this, m_settingsFile );
        {
            QMutexLocker locker ( &m_controllerMutex );
            m_controller = &controller;
            m_ready      = true;
        }
        exec();
        {
            QMutexLocker locker ( &m_controllerMutex );
            m_controller.clear();
            m_ready = false;
        }
    }
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad ( JavaVM*, void* )
{
    if ( QCoreApplication::instance() == nullptr )
    {
        static int   argc          = 1;
        static char  programName[] = "Jamulus Compose";
        static char* argv[]        = { programName, nullptr };
        g_qtApplication            = new QCoreApplication ( argc, argv );
        g_qtApplication->setApplicationName ( QStringLiteral ( "Jamulus Compose" ) );
        g_qtApplication->setOrganizationName ( QStringLiteral ( "Jamulus" ) );
    }
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jlong JNICALL Java_org_jamulus_multiplatform_DesktopNativeBridge_nativeCreate ( JNIEnv* env,
                                                                                                     jobject,
                                                                                                     jobject listener,
                                                                                                     jstring settingsFile )
{
    JavaVM* javaVM = nullptr;
    if ( env->GetJavaVM ( &javaVM ) != JNI_OK || listener == nullptr )
    {
        return 0;
    }

    jclass listenerClass = env->GetObjectClass ( listener );
    if ( listenerClass == nullptr )
    {
        return 0;
    }

    jmethodID invokeMethod = env->GetMethodID ( listenerClass, "invoke", "(Ljava/lang/Object;)Ljava/lang/Object;" );
    if ( invokeMethod == nullptr )
    {
        env->DeleteLocalRef ( listenerClass );
        return 0;
    }
    jobject globalListener      = env->NewGlobalRef ( listener );
    jclass  globalListenerClass = static_cast<jclass> ( env->NewGlobalRef ( listenerClass ) );
    env->DeleteLocalRef ( listenerClass );
    if ( globalListener == nullptr || globalListenerClass == nullptr )
    {
        if ( globalListener != nullptr )
            env->DeleteGlobalRef ( globalListener );
        if ( globalListenerClass != nullptr )
            env->DeleteGlobalRef ( globalListenerClass );
        return 0;
    }

    CComposeEngineThread* engine =
        new CComposeEngineThread ( javaVM, globalListener, globalListenerClass, invokeMethod, FromJavaString ( env, settingsFile ) );
    engine->start();

    const qint64 deadline = QDateTime::currentMSecsSinceEpoch() + 10000;
    while ( !engine->IsReady() && engine->isRunning() && QDateTime::currentMSecsSinceEpoch() < deadline )
    {
        QThread::msleep ( 10 );
    }
    if ( !engine->IsReady() )
    {
        engine->StopAndWait();
        engine->ReleaseJavaRefs ( env );
        delete engine;
        return 0;
    }
    return static_cast<jlong> ( reinterpret_cast<intptr_t> ( engine ) );
}

extern "C" JNIEXPORT void JNICALL Java_org_jamulus_multiplatform_DesktopNativeBridge_nativeClose ( JNIEnv* env, jobject, jlong handle )
{
    CComposeEngineThread* engine = reinterpret_cast<CComposeEngineThread*> ( static_cast<intptr_t> ( handle ) );
    if ( engine == nullptr )
    {
        return;
    }
    engine->StopAndWait();
    engine->ReleaseJavaRefs ( env );
    delete engine;
    Q_UNUSED ( env );
}

extern "C" JNIEXPORT void JNICALL Java_org_jamulus_multiplatform_DesktopNativeBridge_nativeConnect ( JNIEnv* env,
                                                                                                     jobject,
                                                                                                     jlong   handle,
                                                                                                     jstring address )
{
    CComposeEngineThread* engine = reinterpret_cast<CComposeEngineThread*> ( static_cast<intptr_t> ( handle ) );
    if ( engine != nullptr )
    {
        engine->Connect ( FromJavaString ( env, address ) );
    }
}

extern "C" JNIEXPORT void JNICALL Java_org_jamulus_multiplatform_DesktopNativeBridge_nativeDisconnect ( JNIEnv*, jobject, jlong handle )
{
    CComposeEngineThread* engine = reinterpret_cast<CComposeEngineThread*> ( static_cast<intptr_t> ( handle ) );
    if ( engine != nullptr )
        engine->Disconnect();
}

extern "C" JNIEXPORT void JNICALL Java_org_jamulus_multiplatform_DesktopNativeBridge_nativeRefreshServerList ( JNIEnv* env,
                                                                                                               jobject,
                                                                                                               jlong   handle,
                                                                                                               jstring directory )
{
    CComposeEngineThread* engine = reinterpret_cast<CComposeEngineThread*> ( static_cast<intptr_t> ( handle ) );
    if ( engine != nullptr )
        engine->RefreshServerList ( FromJavaString ( env, directory ) );
}

extern "C" JNIEXPORT void JNICALL
Java_org_jamulus_multiplatform_DesktopNativeBridge_nativeSetShowServerMusicians ( JNIEnv*, jobject, jlong handle, jboolean enabled )
{
    CComposeEngineThread* engine = reinterpret_cast<CComposeEngineThread*> ( static_cast<intptr_t> ( handle ) );
    if ( engine != nullptr )
        engine->SetShowServerMusicians ( enabled == JNI_TRUE );
}

extern "C" JNIEXPORT void JNICALL Java_org_jamulus_multiplatform_DesktopNativeBridge_nativeSetSendMuted ( JNIEnv*,
                                                                                                          jobject,
                                                                                                          jlong    handle,
                                                                                                          jboolean muted )
{
    CComposeEngineThread* engine = reinterpret_cast<CComposeEngineThread*> ( static_cast<intptr_t> ( handle ) );
    if ( engine != nullptr )
        engine->SetSendMuted ( muted == JNI_TRUE );
}

extern "C" JNIEXPORT void JNICALL
Java_org_jamulus_multiplatform_DesktopNativeBridge_nativeSetChannelGain ( JNIEnv*, jobject, jlong handle, jint channelId, jfloat gain )
{
    CComposeEngineThread* engine = reinterpret_cast<CComposeEngineThread*> ( static_cast<intptr_t> ( handle ) );
    if ( engine != nullptr )
        engine->SetChannelGain ( channelId, gain );
}

extern "C" JNIEXPORT void JNICALL
Java_org_jamulus_multiplatform_DesktopNativeBridge_nativeSetChannelPan ( JNIEnv*, jobject, jlong handle, jint channelId, jfloat pan )
{
    CComposeEngineThread* engine = reinterpret_cast<CComposeEngineThread*> ( static_cast<intptr_t> ( handle ) );
    if ( engine != nullptr )
        engine->SetChannelPan ( channelId, pan );
}

extern "C" JNIEXPORT void JNICALL
Java_org_jamulus_multiplatform_DesktopNativeBridge_nativeSetChannelMuted ( JNIEnv*, jobject, jlong handle, jint channelId, jboolean muted )
{
    CComposeEngineThread* engine = reinterpret_cast<CComposeEngineThread*> ( static_cast<intptr_t> ( handle ) );
    if ( engine != nullptr )
        engine->SetChannelMuted ( channelId, muted == JNI_TRUE );
}

extern "C" JNIEXPORT void JNICALL
Java_org_jamulus_multiplatform_DesktopNativeBridge_nativeSetChannelSolo ( JNIEnv*, jobject, jlong handle, jint channelId, jboolean solo )
{
    CComposeEngineThread* engine = reinterpret_cast<CComposeEngineThread*> ( static_cast<intptr_t> ( handle ) );
    if ( engine != nullptr )
        engine->SetChannelSolo ( channelId, solo == JNI_TRUE );
}

extern "C" JNIEXPORT void JNICALL Java_org_jamulus_multiplatform_DesktopNativeBridge_nativeSendChat ( JNIEnv* env,
                                                                                                      jobject,
                                                                                                      jlong   handle,
                                                                                                      jstring message )
{
    CComposeEngineThread* engine = reinterpret_cast<CComposeEngineThread*> ( static_cast<intptr_t> ( handle ) );
    if ( engine != nullptr )
        engine->SendChat ( FromJavaString ( env, message ) );
}

extern "C" JNIEXPORT void JNICALL Java_org_jamulus_multiplatform_DesktopNativeBridge_nativeSetClientProfile ( JNIEnv* env,
                                                                                                              jobject,
                                                                                                              jlong   handle,
                                                                                                              jstring alias,
                                                                                                              jint    instrumentId,
                                                                                                              jint    countryId,
                                                                                                              jstring city,
                                                                                                              jint    skillId )
{
    CComposeEngineThread* engine = reinterpret_cast<CComposeEngineThread*> ( static_cast<intptr_t> ( handle ) );
    if ( engine != nullptr )
    {
        engine->SetClientProfile ( FromJavaString ( env, alias ), instrumentId, countryId, FromJavaString ( env, city ), skillId );
    }
}

extern "C" JNIEXPORT void JNICALL Java_org_jamulus_multiplatform_DesktopNativeBridge_nativeSetAudioDevice ( JNIEnv* env,
                                                                                                            jobject,
                                                                                                            jlong   handle,
                                                                                                            jstring device )
{
    CComposeEngineThread* engine = reinterpret_cast<CComposeEngineThread*> ( static_cast<intptr_t> ( handle ) );
    if ( engine != nullptr )
        engine->SetAudioDevice ( FromJavaString ( env, device ) );
}

extern "C" JNIEXPORT void JNICALL Java_org_jamulus_multiplatform_DesktopNativeBridge_nativeSetAudioFormat ( JNIEnv*,
                                                                                                            jobject,
                                                                                                            jlong handle,
                                                                                                            jint  format )
{
    CComposeEngineThread* engine = reinterpret_cast<CComposeEngineThread*> ( static_cast<intptr_t> ( handle ) );
    if ( engine != nullptr )
        engine->SetAudioFormat ( format );
}

extern "C" JNIEXPORT void JNICALL Java_org_jamulus_multiplatform_DesktopNativeBridge_nativeSetAudioChannels ( JNIEnv*,
                                                                                                              jobject,
                                                                                                              jlong handle,
                                                                                                              jint  channels )
{
    CComposeEngineThread* engine = reinterpret_cast<CComposeEngineThread*> ( static_cast<intptr_t> ( handle ) );
    if ( engine != nullptr )
        engine->SetAudioChannels ( channels );
}

extern "C" JNIEXPORT void JNICALL Java_org_jamulus_multiplatform_DesktopNativeBridge_nativeSetSmallNetworkBuffers ( JNIEnv*,
                                                                                                                    jobject,
                                                                                                                    jlong    handle,
                                                                                                                    jboolean enabled )
{
    CComposeEngineThread* engine = reinterpret_cast<CComposeEngineThread*> ( static_cast<intptr_t> ( handle ) );
    if ( engine != nullptr )
        engine->SetSmallNetworkBuffers ( enabled == JNI_TRUE );
}

extern "C" JNIEXPORT void JNICALL Java_org_jamulus_multiplatform_DesktopNativeBridge_nativeSetAudioBufferFactor ( JNIEnv*,
                                                                                                                  jobject,
                                                                                                                  jlong handle,
                                                                                                                  jint  factor )
{
    CComposeEngineThread* engine = reinterpret_cast<CComposeEngineThread*> ( static_cast<intptr_t> ( handle ) );
    if ( engine != nullptr )
        engine->SetAudioBufferFactor ( factor );
}

extern "C" JNIEXPORT void JNICALL Java_org_jamulus_multiplatform_DesktopNativeBridge_nativeSetAutomaticJitterBuffer ( JNIEnv*,
                                                                                                                      jobject,
                                                                                                                      jlong    handle,
                                                                                                                      jboolean enabled )
{
    CComposeEngineThread* engine = reinterpret_cast<CComposeEngineThread*> ( static_cast<intptr_t> ( handle ) );
    if ( engine != nullptr )
        engine->SetAutomaticJitterBuffer ( enabled == JNI_TRUE );
}

extern "C" JNIEXPORT void JNICALL Java_org_jamulus_multiplatform_DesktopNativeBridge_nativeSetJitterBufferFrames ( JNIEnv*,
                                                                                                                   jobject,
                                                                                                                   jlong handle,
                                                                                                                   jint  frames )
{
    CComposeEngineThread* engine = reinterpret_cast<CComposeEngineThread*> ( static_cast<intptr_t> ( handle ) );
    if ( engine != nullptr )
        engine->SetJitterBufferFrames ( frames );
}

extern "C" JNIEXPORT void JNICALL Java_org_jamulus_multiplatform_DesktopNativeBridge_nativeSetServerJitterBufferFrames ( JNIEnv*,
                                                                                                                         jobject,
                                                                                                                         jlong handle,
                                                                                                                         jint  frames )
{
    CComposeEngineThread* engine = reinterpret_cast<CComposeEngineThread*> ( static_cast<intptr_t> ( handle ) );
    if ( engine != nullptr )
        engine->SetServerJitterBufferFrames ( frames );
}

#include "compose_engine.moc"
