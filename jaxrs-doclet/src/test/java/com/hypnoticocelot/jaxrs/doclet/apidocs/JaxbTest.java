package com.hypnoticocelot.jaxrs.doclet.apidocs;

import com.hypnoticocelot.jaxrs.doclet.DocletOptions;
import com.hypnoticocelot.jaxrs.doclet.Recorder;
import com.hypnoticocelot.jaxrs.doclet.model.ApiDeclaration;
import com.hypnoticocelot.jaxrs.doclet.parser.JaxRsAnnotationParser;
import com.sun.javadoc.RootDoc;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static com.hypnoticocelot.jaxrs.doclet.apidocs.FixtureLoader.loadFixture;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class JaxbTest {

    private Recorder recorderMock;
    private DocletOptions options;

    @Before
    public void setup() {
        recorderMock = mock(Recorder.class);
        options = new DocletOptions().setRecorder(recorderMock);
    }

    @Test
    public void testStart() throws IOException {
        final RootDoc rootDoc = RootDocLoader.fromPath("jaxrs-doclet/src/test/resources", "fixtures.jaxb");
        new JaxRsAnnotationParser(options, rootDoc).run();

        final ApiDeclaration api = loadFixture("/fixtures/jaxb/jaxb.json", ApiDeclaration.class);
        verify(recorderMock).record(any(File.class), eq(api));
    }

}
