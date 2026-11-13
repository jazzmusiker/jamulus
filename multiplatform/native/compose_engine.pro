# Copyright (c) 2026 Thomas Mueller
# Author(s): Thomas Mueller
# Licensed under AGPL 3.0 or any later version. See COPYING for details.

TEMPLATE = lib
CONFIG += headless nojsonrpc
JAMULUS_ROOT = $$clean_path($$_PRO_FILE_PWD_/../..)

include(../../Jamulus.pro)

TEMPLATE = lib
TARGET = jamuluscompose
SOURCES -= src/main.cpp

JAMULUS_SOURCES =
for ( source, SOURCES ) {
    JAMULUS_SOURCES += $$absolute_path($$source, $$JAMULUS_ROOT)
}
SOURCES = $$JAMULUS_SOURCES

JAMULUS_HEADERS =
for ( header, HEADERS ) {
    JAMULUS_HEADERS += $$absolute_path($$header, $$JAMULUS_ROOT)
}
HEADERS = $$JAMULUS_HEADERS

JAMULUS_INCLUDEPATH =
for ( include, INCLUDEPATH ) {
    JAMULUS_INCLUDEPATH += $$absolute_path($$include, $$JAMULUS_ROOT)
}
INCLUDEPATH = $$JAMULUS_INCLUDEPATH

JAMULUS_RESOURCES =
for ( resource, RESOURCES ) {
    JAMULUS_RESOURCES += $$absolute_path($$resource, $$JAMULUS_ROOT)
}
RESOURCES = $$JAMULUS_RESOURCES

JAMULUS_DISTFILES =
for ( distfile, DISTFILES ) {
    JAMULUS_DISTFILES += $$absolute_path($$distfile, $$JAMULUS_ROOT)
}
DISTFILES = $$JAMULUS_DISTFILES

SOURCES += $$JAMULUS_ROOT/multiplatform/native/compose_engine.cpp
HEADERS += $$JAMULUS_ROOT/multiplatform/native/compose_engine.h
OBJECTIVE_SOURCES -= src/mac/activity.mm src/mac/badgelabel.mm
CONFIG -= lrelease embed_translations
TRANSLATIONS =
android {
    QT -= androidextras
    QMAKE_LFLAGS += -Wl,-z,max-page-size=16384
    OBJECTS_DIR = $$OUT_PWD/obj
    MOC_DIR = $$OUT_PWD/moc
    RCC_DIR = $$OUT_PWD/rcc
    DESTDIR = $$OUT_PWD/lib
} else {
    OBJECTS_DIR = $$JAMULUS_ROOT/multiplatform/native/build/obj
    MOC_DIR = $$JAMULUS_ROOT/multiplatform/native/build/moc
    RCC_DIR = $$JAMULUS_ROOT/multiplatform/native/build/rcc
    DESTDIR = $$JAMULUS_ROOT/multiplatform/native/build
}
VERSION = 1.0.0
QMAKE_INFO_PLIST =

!android {
    JNI_HOME = $$(JAVA_HOME)
    isEmpty(JNI_HOME) {
        error("JAVA_HOME is required to compile the Compose JNI bridge.")
    }
    QMAKE_CXXFLAGS += -I$$JNI_HOME/include
    macx: QMAKE_CXXFLAGS += -I$$JNI_HOME/include/darwin
    win32: QMAKE_CXXFLAGS += -I$$JNI_HOME/include/win32
    unix:!macx: QMAKE_CXXFLAGS += -I$$JNI_HOME/include/linux
}

DEFINES += JAMULUS_COMPOSE_ENGINE
