package com.comeforfood.saferoute;
import androidx.lifecycle.SavedStateHandle;
import com.comeforfood.saferoute.feature.report.ReportDraft;
import org.junit.Test;
import static org.junit.Assert.*;
public class DraftCategoryMigrationTest {
    @Test public void oldDraftCategoriesAreMappedWithoutDiscardingUncertainWrites() {
        int[] expected={0,0,0,1,-1,2,3};
        for(int old=0;old<expected.length;old++) {
            SavedStateHandle state=new SavedStateHandle();
            state.set("category",old);state.set("confirmed",true);state.set("uncertain",true);
            state.set("description","Keep this draft");
            ReportDraft draft=new ReportDraft(state);
            assertEquals(expected[old],draft.category());
            assertFalse(draft.confirmed());assertTrue(draft.uncertain());
            assertEquals("Keep this draft",draft.description());
            assertEquals(expected[old],draft.category()); // Migration is applied exactly once.
        }
    }
    @Test public void newCategorySelectionSurvivesRestoreWithoutLegacyRemapping() {
        SavedStateHandle state=new SavedStateHandle();
        ReportDraft draft=new ReportDraft(state);draft.category(3);draft.confirmed(true);
        ReportDraft restored=new ReportDraft(state);
        assertEquals(3,restored.category());assertTrue(restored.confirmed());
        restored.clear();assertEquals(-1,restored.category());
    }
}
