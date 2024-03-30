import com.android.ndkports.AutoconfPortTask
import com.android.ndkports.CMakeCompatibleVersion
import com.android.ndkports.PrefabSysrootPlugin

val portVersion = "7.79.1"

group = "com.android.ndk.thirdparty"
version = "$portVersion${rootProject.extra.get("snapshotSuffix")}"

plugins {
    id("maven-publish")
    id("com.android.ndkports.NdkPorts")
}

dependencies {
    implementation(project(":openssl"))
}

ndkPorts {
    ndkPath.set(File(project.findProperty("ndkPath") as String))
    source.set(project.file("src.tar.gz"))
    minSdkVersion.set(16)
}

tasks.prefab {
    generator.set(PrefabSysrootPlugin::class.java)
}

tasks.register<AutoconfPortTask>("buildPort") {
    autoconf {
        args(
            "--disable-ntlm-wb",
            "--enable-ipv6",
            "--with-zlib",
            "--with-ca-path=/system/etc/security/cacerts",
            "--with-ssl=$sysroot"
        )

        // aarch64 still defaults to bfd which transitively checks libraries.
        // When curl is linking one of its own libraries which depends on
        // openssl, it doesn't pass -rpath-link to be able to find the SSL
        // libraries and fails to build because of it.
        //
        // TODO: Switch to lld once we're using r21.
        env("LDFLAGS", "-fuse-ld=gold")
    }
}

tasks.prefabPackage {
    version.set(CMakeCompatibleVersion.parse(portVersion))

    licensePath.set("COPYING")

    @Suppress("UnstableApiUsage") dependencies.set(
        mapOf(
            "openssl" to "1.1.1k"
        )
    )

    modules {
        create("curl") {
            dependencies.set(
                listOf(
                    "//openssl:crypto", "//openssl:ssl"
                )
            )
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["prefab"])
            pom {
                name.set("curl")
                description.set("The ndkports AAR for curl.")
                url.set(
                    "https://android.googlesource.com/platform/tools/ndkports"
                )
                licenses {
                    license {
                        name.set("The curl License")
                        url.set("https://curl.haxx.se/docs/copyright.html")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        name.set("The Android Open Source Project")
                    }
                }
                scm {
                    url.set("https://android.googlesource.com/platform/tools/ndkports")
                    connection.set("scm:git:https://android.googlesource.com/platform/tools/ndkports")
                }
            }
        }
    }

    repositories {
        maven {
            url = uri("${rootProject.buildDir}/repository")
        }
    }
}
