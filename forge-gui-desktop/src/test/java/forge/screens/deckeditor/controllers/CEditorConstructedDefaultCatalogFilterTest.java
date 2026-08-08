package forge.screens.deckeditor.controllers;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;

public class CEditorConstructedDefaultCatalogFilterTest {
    @Test
    public void constructedCatalogDefaultsToDiySetsOnly() {
        Assert.assertEquals(CEditorConstructed.getDefaultCatalogSetCodes(), Arrays.asList("BT3K", "PH01"));
    }
}
