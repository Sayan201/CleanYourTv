package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WallpaperContrastTest {

    @Test
    public void blackIsDark() {
        int lum = MainActivity.calculateLuminance(0, 0, 0);
        assertEquals(0, lum);
        assertFalse(MainActivity.isLuminanceBright(lum, 0));
    }

    @Test
    public void whiteIsBright() {
        int lum = MainActivity.calculateLuminance(255, 255, 255);
        assertEquals(255_000, lum);
        assertTrue(MainActivity.isLuminanceBright(lum, 100));
    }

    @Test
    public void darkGrayStaysDarkTheme() {
        int lum = MainActivity.calculateLuminance(60, 60, 60);
        assertEquals(60_000, lum);
        assertFalse(MainActivity.isLuminanceBright(lum, 0));
    }

    @Test
    public void lightPastelTriggersDarkContrastFont() {
        // Light yellow / beige sky (240, 235, 210)
        int lum = MainActivity.calculateLuminance(240, 235, 210);
        assertTrue(lum > 140_000);
        assertTrue(MainActivity.isLuminanceBright(lum, 100));
    }

    @Test
    public void nullBitmapDefaultsToNormalDarkBackdrop() {
        assertFalse(MainActivity.isWallpaperTopBright(null));
    }

    @Test
    public void brightRatioThresholdTriggersContrast() {
        // Average slightly below 122_400 (e.g. 110_000), but more than 40% of upper sky is bright (e.g. 45%)
        assertTrue(MainActivity.isLuminanceBright(110_000, 45));
        // Average low and ratio low: stays normal
        assertFalse(MainActivity.isLuminanceBright(90_000, 20));
    }
}
