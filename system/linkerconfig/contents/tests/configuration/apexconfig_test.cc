/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "linkerconfig/apexconfig.h"

#include <android-base/file.h>
#include <apex_manifest.pb.h>
#include <gtest/gtest.h>
#include <vector>

#include "configurationtest.h"
#include "linkerconfig/apex.h"
#include "linkerconfig/configwriter.h"
#include "mockenv.h"

using android::linkerconfig::modules::ApexInfo;

namespace {
struct ApexConfigTest : ::testing::Test {
  void SetUp() override {
    MockGenericVariables();
  }

  ApexInfo PrepareApex(const std::string& apex_name,
                       const std::vector<std::string>& provide_libs,
                       const std::vector<std::string>& require_libs) {
    return ApexInfo(apex_name,
                    "/apex/" + apex_name,
                    provide_libs,
                    require_libs,
                    /*jni_libs=*/{},
                    /*permitted_paths=*/{},
                    /*contributions=*/{},
                    /*has_bin=*/true,
                    /*has_lib=*/true,
                    /*visible=*/false,
                    /*has_shared_lib=*/false);
  }
};
}  // namespace

TEST_F(ApexConfigTest, apex_no_dependency) {
  android::linkerconfig::contents::Context ctx;
  auto target_apex = PrepareApex("target", {}, {});
  auto config = android::linkerconfig::contents::CreateApexConfiguration(
      ctx, target_apex);

  android::linkerconfig::modules::ConfigWriter config_writer;
  config.WriteConfig(config_writer);

  VerifyConfiguration(config_writer.ToString());
}

TEST_F(ApexConfigTest, apex_with_required) {
  android::linkerconfig::contents::Context ctx;
  ctx.AddApexModule(PrepareApex("foo", {"a.so"}, {"b.so"}));
  ctx.AddApexModule(PrepareApex("bar", {"b.so"}, {}));
  ctx.AddApexModule(PrepareApex("baz", {"c.so"}, {"a.so"}));
  auto target_apex = PrepareApex("target", {}, {"a.so", "b.so"});
  auto config = android::linkerconfig::contents::CreateApexConfiguration(
      ctx, target_apex);

  android::linkerconfig::modules::ConfigWriter config_writer;
  config.WriteConfig(config_writer);

  VerifyConfiguration(config_writer.ToString());
}

TEST_F(ApexConfigTest, vndk_in_system_vendor_apex) {
  MockVndkUsingCoreVariant();
  android::linkerconfig::contents::Context ctx = GenerateContextWithVndk();

  android::linkerconfig::proto::LinkerConfig vendor_config;
  vendor_config.add_providelibs("libvendorprovide.so");
  ctx.SetVendorConfig(vendor_config);

  auto vendor_apex =
      PrepareApex("vendor_apex", {}, {":vndk", "libvendorprovide.so"});
  vendor_apex.original_path = "/vendor/apex/com.android.vendor";
  ctx.AddApexModule(vendor_apex);
  auto config = android::linkerconfig::contents::CreateApexConfiguration(
      ctx, vendor_apex);

  android::linkerconfig::modules::ConfigWriter config_writer;
  config.WriteConfig(config_writer);

  VerifyConfiguration(config_writer.ToString());
}

TEST_F(ApexConfigTest, vndk_in_system_product_apex) {
  MockVndkUsingCoreVariant();
  android::linkerconfig::contents::Context ctx = GenerateContextWithVndk();

  android::linkerconfig::proto::LinkerConfig product_config;
  product_config.add_providelibs("libproductprovide.so");
  ctx.SetProductConfig(product_config);

  auto product_apex =
      PrepareApex("product_apex", {}, {":vndksp", "libproductprovide.so"});
  product_apex.original_path = "/product/apex/com.android.product";
  ctx.AddApexModule(product_apex);
  auto config = android::linkerconfig::contents::CreateApexConfiguration(
      ctx, product_apex);

  android::linkerconfig::modules::ConfigWriter config_writer;
  config.WriteConfig(config_writer);

  VerifyConfiguration(config_writer.ToString());
}

TEST_F(ApexConfigTest, vendor_apex_without_use_vndk_as_stable) {
  android::linkerconfig::contents::Context ctx = GenerateContextWithVndk();

  android::linkerconfig::proto::LinkerConfig vendor_config;
  vendor_config.add_requirelibs("libapexprovide.so");
  vendor_config.add_providelibs("libvendorprovide.so");
  ctx.SetVendorConfig(vendor_config);

  // Vendor apex requires :vndk
  auto vendor_apex = PrepareApex(
      "vendor_apex", {"libapexprovide.so"}, {"libvendorprovide.so"});
  vendor_apex.original_path = "/vendor/apex/com.android.vendor";
  ctx.AddApexModule(vendor_apex);

  auto config = CreateApexConfiguration(ctx, vendor_apex);

  auto* section = config.GetSection("vendor_apex");
  ASSERT_TRUE(section);

  // vendor apex should be able to load vndk libraries
  auto vndk_namespace = section->GetNamespace("vndk");
  ASSERT_TRUE(vndk_namespace);

  android::linkerconfig::modules::ConfigWriter config_writer;
  config.WriteConfig(config_writer);
  VerifyConfiguration(config_writer.ToString());
}
