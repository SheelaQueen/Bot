package me.deprilula28.gamesrobshardcluster.utilities;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

public class CommandProcessorClassLoader extends URLClassLoader {
    public CommandProcessorClassLoader(File file, ClassLoader parent) throws MalformedURLException {
        super(new URL[] { file.toURI().toURL() }, parent);
    }

    @Override
    public URL getResource(String name) {
        return super.getResource(name);
    }
}
