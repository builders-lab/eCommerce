package com.ecommerce.platform.architecture;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleBoundaryTest {

    private static final String MODULE_ROOT = "com.ecommerce.platform.modules.";

    @Test
    void modules_do_not_import_other_modules_repositories_or_models() {
        List<String> violations = new ArrayList<>();

        for (JavaClass origin : mainModuleClasses()) {
            String originModule = moduleName(origin.getPackageName());
            if (originModule == null) {
                continue;
            }

            for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                String targetModule = moduleName(target.getPackageName());
                if (targetModule == null || originModule.equals(targetModule)) {
                    continue;
                }

                String targetPackage = target.getPackageName();
                if (targetPackage.contains(".repository") || targetPackage.contains(".model")) {
                    violations.add(origin.getName() + " depends on " + target.getName());
                }
            }
        }

        assertThat(violations).isEmpty();
    }

    @Test
    void users_controller_does_not_depend_on_auth_service_or_auth_dtos() {
        List<String> violations = new ArrayList<>();

        for (JavaClass origin : mainModuleClasses()) {
            if (!origin.getPackageName().equals("com.ecommerce.platform.modules.users.controller")) {
                continue;
            }

            for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                String targetPackage = target.getPackageName();
                if (targetPackage.equals("com.ecommerce.platform.modules.auth.service") ||
                        targetPackage.equals("com.ecommerce.platform.modules.auth.dto")) {
                    violations.add(origin.getName() + " depends on " + target.getName());
                }
            }
        }

        assertThat(violations).isEmpty();
    }

    private Iterable<JavaClass> mainModuleClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.ecommerce.platform.modules");
    }

    private String moduleName(String packageName) {
        int index = packageName.indexOf(MODULE_ROOT);
        if (index < 0) {
            return null;
        }

        String remainder = packageName.substring(index + MODULE_ROOT.length());
        int separator = remainder.indexOf('.');
        return separator < 0 ? remainder : remainder.substring(0, separator);
    }
}
