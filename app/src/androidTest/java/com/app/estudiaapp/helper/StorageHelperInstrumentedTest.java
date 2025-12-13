package com.app.estudiaapp.helper;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.app.estudiaapp.models.StudyPlan;
import java.io.File;
import java.util.Collections;
import org.junit.Test;

public class StorageHelperInstrumentedTest {

    @Test
    public void saveAndLoadPlan_happyPath() {
        Context ctx = ApplicationProvider.getApplicationContext();
        StudyPlan plan = new StudyPlan("U00002", "Plan local");
        plan.quizzes = Collections.emptyList();

        StorageHelper.guardarPlan(ctx, plan);

        StudyPlan loaded = StorageHelper.cargarPlan(ctx, "U00002");
        assertNotNull(loaded);
        assertEquals("U00002", loaded.id);
        assertEquals("Plan local", loaded.nombre);
    }

    @Test
    public void loadPlan_corruptedFile_returnsNull() {
        Context ctx = ApplicationProvider.getApplicationContext();
        File dir = ctx.getFilesDir();
        File f = new File(dir, "plan_U00003.json");
        TestIo.writeString(f, "{corrupt json");

        StudyPlan loaded = StorageHelper.cargarPlan(ctx, "U00003");
        assertNull(loaded);
    }

    static class TestIo {
        static void writeString(File file, String content) {
            try (java.io.FileWriter fw = new java.io.FileWriter(file)) {
                fw.write(content);
            } catch (Exception ignored) {}
        }
    }
}
