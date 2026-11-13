/*
 * Copyright (c) 2026 Thomas Mueller
 * Author(s): Thomas Mueller
 * Licensed under AGPL 3.0 or any later version. See COPYING for details.
 */

#pragma once

#include <jni.h>
#include <QMutex>
#include <QObject>
#include <QPointer>
#include <QThread>

class CComposeEngineController;

class CComposeEngineThread : public QThread
{
    Q_OBJECT

public:
    CComposeEngineThread ( JavaVM* javaVM, jobject listener, jclass listenerClass, jmethodID invokeMethod, const QString& settingsFile );
    ~CComposeEngineThread() override;

    bool IsReady() const;
    void NotifySnapshot ( const QString& payload );
    void ReleaseJavaRefs ( JNIEnv* env );
    void StopAndWait();
    void Connect ( const QString& address );
    void Disconnect();
    void RefreshServerList ( const QString& directory );
    void SetSendMuted ( bool muted );
    void SetChannelGain ( int channelId, float gain );
    void SetChannelPan ( int channelId, float pan );
    void SetChannelMuted ( int channelId, bool muted );
    void SetChannelSolo ( int channelId, bool solo );
    void SendChat ( const QString& message );
    void SetClientProfile ( const QString& alias, int instrumentId, int countryId, const QString& city, int skillId );
    void SetAudioDevice ( const QString& device );
    void SetAudioFormat ( int format );
    void SetAudioChannels ( int channels );
    void SetSmallNetworkBuffers ( bool enabled );
    void SetAudioBufferFactor ( int factor );
    void SetAutomaticJitterBuffer ( bool enabled );
    void SetJitterBufferFrames ( int frames );
    void SetServerJitterBufferFrames ( int frames );

protected:
    void run() override;

private:
    JavaVM*                            m_javaVM;
    jobject                            m_listener;
    jclass                             m_listenerClass;
    jmethodID                          m_invokeMethod;
    QString                            m_settingsFile;
    mutable QMutex                     m_controllerMutex;
    QPointer<CComposeEngineController> m_controller;
    bool                               m_ready;
};
