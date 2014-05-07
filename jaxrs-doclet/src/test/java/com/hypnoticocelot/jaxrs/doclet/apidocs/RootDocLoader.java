package com.hypnoticocelot.jaxrs.doclet.apidocs;

import com.sun.javadoc.RootDoc;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Options;
import com.sun.tools.javadoc.JavadocTool;
import com.sun.tools.javadoc.Messager;
import com.sun.tools.javadoc.ModifierFilter;

import java.io.IOException;

public class RootDocLoader {

    private RootDocLoader() {
    }

    public static RootDoc fromPath(String path, String subpackage) throws IOException {
        final Context context = new Context();
        Options.instance(context).put("-sourcepath", path);
        Messager.preRegister(context, "Messager!");

        final ListBuffer<String> subPackages = new ListBuffer<String>();
        subPackages.append(subpackage);

        final JavadocTool javaDoc = JavadocTool.make0(context);
        return javaDoc.getRootDocImpl(
                "", //doclocale
                null, //encoding
                new ModifierFilter(ModifierFilter.ALL_ACCESS), //filter
                new ListBuffer<String>().toList(), //javaNames
                new ListBuffer<String[]>().toList(), //options
                false, //breakiterator
                subPackages.toList(), //subPackages
                new ListBuffer<String>().toList(), //excludedPackages
                false, //docClasses
                false, //legacyDoclet
                false //quiet
        );
    }

}
