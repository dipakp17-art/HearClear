package com.dipak.hearclear;

import org.junit.Test;
import java.nio.file.Files;

public final class ListeningBaselineTest {
    @Test public void filterResponse() { SpeechFocusFilterCheck.main(new String[0]); }
    @Test public void gainLimiter() { VoiceLevelerCheck.main(new String[0]); }
    @Test public void stereoEars() { StereoPcmCheck.main(new String[0]); }
    @Test public void comparisonFixtures() throws Exception {
        ListeningComparisonCheck.main(new String[]{Files.createTempDirectory("listening-compare").toString()});
    }
    @Test public void comparisonInputWavRoundTrip() throws Exception {
        java.nio.file.Path fixtures = Files.createTempDirectory("listening-wav");
        ListeningComparisonCheck.main(new String[]{fixtures.toString()});
        ListeningComparisonCheck.main(new String[]{
            fixtures.resolve("speech_both_off.wav").toString(),
            fixtures.resolve("fan_both_off.wav").toString(),
            fixtures.resolve("round-trip").toString()
        });
    }
}


