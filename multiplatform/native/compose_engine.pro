# Copyright (c) 2026 Thomas Mueller
# Author(s): Thomas Mueller
# Licensed under AGPL 3.0 or any later version. See COPYING for details.

TEMPLATE = lib
CONFIG += headless nojsonrpc
JAMULUS_ROOT = $$clean_path($$PWD/../..)

include(../../Jamulus.pro)

TEMPLATE = lib
TARGET = jamuluscompose
SOURCES -= src/main.cpp
SOURCES += $$JAMULUS_ROOT/multiplatform/native/compose_engine.cpp
HEADERS += $$JAMULUS_ROOT/multiplatform/native/compose_engine.h
OBJECTIVE_SOURCES -= src/mac/activity.mm src/mac/badgelabel.mm
CONFIG -= lrelease embed_translations
TRANSLATIONS =
OBJECTS_DIR = $$JAMULUS_ROOT/multiplatform/native/build/obj
MOC_DIR = $$JAMULUS_ROOT/multiplatform/native/build/moc
RCC_DIR = $$JAMULUS_ROOT/multiplatform/native/build/rcc
DESTDIR = $$JAMULUS_ROOT/multiplatform/native/build
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
