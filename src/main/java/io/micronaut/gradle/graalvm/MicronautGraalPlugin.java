package io.micronaut.gradle.graalvm;

import io.micronaut.gradle.MicronautExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Support for building GraalVM native images.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class MicronautGraalPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.afterEvaluate(p -> {
            MicronautExtension extension = p.getExtensions().getByType(MicronautExtension.class);
            if (extension.getEnableNativeImage().getOrElse(false)) {
                SourceSetContainer sourceSets = p.getConvention().getPlugin(JavaPluginConvention.class)
                        .getSourceSets();
                for (String sourceSetName : Arrays.asList("main", "test")) {
                    SourceSet sourceSet = sourceSets.findByName(sourceSetName);
                    if (sourceSet != null) {
                        p.getDependencies().add(
                                sourceSet.getAnnotationProcessorConfigurationName(),
                                "io.micronaut:micronaut-graal"
                        );
                    }
                }
                ListProperty<SourceSet> additionalSourceSets = extension.getProcessing().getAdditionalSourceSets();
                if (additionalSourceSets.isPresent()) {
                    List<SourceSet> sourceSetList = additionalSourceSets.get();
                    for (SourceSet sourceSet : sourceSetList) {
                        p.getDependencies().add(
                                sourceSet.getAnnotationProcessorConfigurationName(),
                                "io.micronaut:micronaut-graal"
                        );
                    }
                }
            }
        });

        if (project.getPlugins().hasPlugin("application")) {
            TaskContainer tasks = project.getTasks();
            tasks.register("nativeImage", NativeImageTask.class, nativeImageTask -> {
                nativeImageTask.dependsOn(tasks.findByName("classes"));
                nativeImageTask.setGroup(BasePlugin.BUILD_GROUP);
                nativeImageTask.setDescription("Builds a GraalVM Native Image");
            });

            project.afterEvaluate(p -> p.getTasks().withType(NativeImageTask.class, nativeImageTask -> {
                MicronautExtension extension = project.getExtensions().getByType(MicronautExtension.class);
                nativeImageTask.setEnabled(extension.getEnableNativeImage().getOrElse(false));
                JavaApplication javaApplication = p.getExtensions().getByType(JavaApplication.class);
                String mainClassName = javaApplication.getMainClass().get();
                String imageName = p.getName();
                nativeImageTask.setMain(mainClassName);
                if (!nativeImageTask.getImageName().isPresent()) {
                    nativeImageTask.setImageName(imageName);
                }
                FileCollection runtimeConfig = p
                        .getConfigurations()
                        .getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME);
                SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
                SourceSet mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
                FileCollection outputDirs = mainSourceSet.getOutput().getClassesDirs();
                runtimeConfig = runtimeConfig.plus(outputDirs);
                Set<File> resourceDirs = mainSourceSet.getResources().getSrcDirs();
                runtimeConfig = runtimeConfig.plus(project.files(resourceDirs));
                nativeImageTask.setClasspath(runtimeConfig);
            }));
        }
    }
}
