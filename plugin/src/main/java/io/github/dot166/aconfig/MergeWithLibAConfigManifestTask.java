package io.github.dot166.aconfig;

//import com.android.build.gradle.BaseExtension;
//import com.android.manifmerger.ManifestMerger2;
//import com.android.manifmerger.MergingReport;
//import com.android.utils.StdLogger;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.nio.file.Files;

public abstract class MergeWithLibAConfigManifestTask extends DefaultTask {
    // No constructor

    @InputFile
    public abstract RegularFileProperty getInputManifest();

    @OutputFile
    public abstract RegularFileProperty getOutputManifest();

    @TaskAction
    public void merge() throws Exception {
        File input = getInputManifest().getAsFile().get();
        File output = getOutputManifest().getAsFile().get();
        File extraManifest = new File(new File(getProject().getBuildDir(), "libaconfig"), "AndroidManifest.xml");

//        StdLogger logger = new StdLogger(StdLogger.Level.VERBOSE);
//
//        ManifestMerger2.Invoker invoker = ManifestMerger2
//                .newMerger(input, logger, ManifestMerger2.MergeType.APPLICATION);
//
//        invoker.addLibraryManifest(extra);
//
//        MergingReport report = invoker.merge();
//        String merged = report.getMergedDocument(MergingReport.MergedManifestKind.MERGED);
//        if (merged == null) {
//            throw new RuntimeException("Manifest merge failed:\n" + report.getReportString());
//        }
//
//        Files.createDirectories(output.getParentFile().toPath());
//        Files.writeString(output.toPath(), merged);
        output.delete(); // clear output file
        String base = Files.readString(input.toPath());
        String extra = Files.readString(extraManifest.toPath());

        // Clean the extra content
        String cleanedExtra = extra
                .replaceAll("<\\?xml.*?\\?>", "")
                .replaceAll("(?s)<manifest[^>]*>", "")
                .replaceAll("</manifest>", "")
                .replaceAll("</application>", "") // In case someone wraps it
                .replaceAll("<application>", "") // In case someone wraps it
                .replaceAll("io.github.dot166.libaconfig", getProject().getExtensions().getByType(AConfigExtension.class).flagsPackage) // allows for flagconfigactivity to be included in libraries without conflicts
                .trim();

        // Clean the extra content
        String cleanedExtraWithApplicationBlock = extra
                .replaceAll("<\\?xml.*?\\?>", "")
                .replaceAll("(?s)<manifest[^>]*>", "")
                .replaceAll("</manifest>", "")
                .replaceAll("io.github.dot166.libaconfig", getProject().getExtensions().getByType(AConfigExtension.class).flagsPackage) // allows for flagconfigactivity to be included in libraries without conflicts
                .trim();

        String merged;

        if (!base.contains(cleanedExtra)) {
            if (base.contains("<application/>")) {
                throw new Exception("invalid manifest");
            }
            if (base.contains("</application>")) {
                // Inject just before </application>
                merged = base.replaceFirst("</application>", cleanedExtra + "\n</application>");
            } else {
                // Inject application block before manifest end
                merged = base.replaceFirst("</manifest>", cleanedExtraWithApplicationBlock + "\n</manifest>");
            }
        } else {
            merged = base; // should be impossible with newer libaconfig and newer gradle-aconfig plugin
        }

        Files.writeString(output.toPath(), merged);
    }
}
