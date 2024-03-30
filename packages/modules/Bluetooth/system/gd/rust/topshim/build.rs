extern crate bindgen;

use pkg_config::Config;
use std::env;
use std::path::PathBuf;

fn main() {
    // Re-run build if any of these change
    println!("cargo:rerun-if-changed=bindings/wrapper.hpp");
    println!("cargo:rerun-if-changed=build.rs");

    // We need to configure libchrome and libmodp_b64 settings as well
    let libchrome = Config::new().probe("libchrome").unwrap();
    let libchrome_paths = libchrome
        .include_paths
        .iter()
        .map(|p| format!("-I{}", p.to_str().unwrap()))
        .collect::<Vec<String>>();

    let search_root = env::var("CXX_ROOT_PATH").unwrap();
    let paths = vec![
        "/system/",
        "/system/btcore",
        "/system/include",
        "/system/include/hardware",
        "/system/types",
    ];

    let bt_searches =
        paths.iter().map(|tail| format!("-I{}{}", search_root, tail)).collect::<Vec<String>>();

    // Also re-run the build if anything in the C++ build changes
    for path in bt_searches.iter() {
        println!("cargo:rerun-if-changed={}", path);
    }

    // "-x" and "c++" must be separate due to a bug
    let clang_args: Vec<&str> = vec!["-x", "c++", "-std=c++17"];

    // The bindgen::Builder is the main entry point
    // to bindgen, and lets you build up options for
    // the resulting bindings.
    let bindings = bindgen::Builder::default()
        .clang_args(bt_searches)
        .clang_args(libchrome_paths)
        .clang_args(clang_args)
        .enable_cxx_namespaces()
        .size_t_is_usize(true)
        .allowlist_type("(bt_|bthh_|btgatt_|btsdp|bluetooth_sdp).*")
        .allowlist_function("(bt_|bthh_|btgatt_|btsdp).*")
        .allowlist_function("hal_util_.*")
        // We must opaque out std:: in order to prevent bindgen from choking
        .opaque_type("std::.*")
        // Whitelist std::string though because we use it a lot
        .allowlist_type("std::string")
        .rustfmt_bindings(true)
        .derive_debug(true)
        .derive_partialeq(true)
        .derive_eq(true)
        .derive_default(true)
        .header("bindings/wrapper.hpp")
        .generate()
        .expect("Unable to generate bindings");

    // Write the bindings to the $OUT_DIR/bindings.rs file.
    let out_path = PathBuf::from(env::var("OUT_DIR").unwrap());
    bindings.write_to_file(out_path.join("bindings.rs")).expect("Couldn't write bindings!");
}
