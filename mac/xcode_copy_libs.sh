mkdir ~/projects/jamulus/Debug/Jamulus.app/Contents/Frameworks

mkdir ~/projects/jamulus/Debug/Jamulus.app/Contents/MacOS/platforms

cp /opt/homebrew/Cellar/qt@5/5.15.18/plugins/platforms/libqcocoa.dylib ~/projects/jamulus/Debug/Jamulus.app/Contents/MacOS/platforms

#cp -R /opt/homebrew/lib/QtDBus.framework ~/projects/jamulus/Debug/Jamulus.app/Contents/Frameworks/QtDBus.framework
cp -R /opt/homebrew/Cellar/qt@5/5.15.18/lib/QtDBus.framework ~/projects/jamulus/Debug/Jamulus.app/Contents/Frameworks/QtDBus.framework

cp /opt/homebrew/opt/freetype/lib/libfreetype.6.dylib ~/projects/jamulus/Debug/Jamulus.app/Contents/Frameworks

codesign --force --deep --sign "-" ~/projects/jamulus/Debug/Jamulus.app
