/**************************************************************************
 *
 * Copyright (C) 2014 Red Hat Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 **************************************************************************/

#ifndef VREND_SHADER_H
#define VREND_SHADER_H

#include "pipe/p_state.h"
#include "pipe/p_shader_tokens.h"

#include "vrend_strbuf.h"

enum gl_advanced_blend_mode
{
   BLEND_NONE = 0,
   BLEND_MULTIPLY,
   BLEND_SCREEN,
   BLEND_OVERLAY,
   BLEND_DARKEN,
   BLEND_LIGHTEN,
   BLEND_COLORDODGE,
   BLEND_COLORBURN,
   BLEND_HARDLIGHT,
   BLEND_SOFTLIGHT,
   BLEND_DIFFERENCE,
   BLEND_EXCLUSION,
   BLEND_HSL_HUE,
   BLEND_HSL_SATURATION,
   BLEND_HSL_COLOR,
   BLEND_HSL_LUMINOSITY,
   BLEND_ALL
};


/* need to store patching info for interpolation */
struct vrend_interp_info {
   unsigned semantic_name : 6;
   unsigned semantic_index : 16;
   unsigned interpolate : 3;
   unsigned location : 3;
};

struct vrend_array {
   int first;
   int array_size;
};

struct vrend_layout_info {
   unsigned name : 6;
   unsigned sid : 16 ;
   unsigned location : 16 ;
   unsigned array_id : 16 ;
   unsigned usage_mask : 5;
};

struct vrend_fs_shader_info {
   int num_interps;
   int glsl_ver;
   bool has_sample_input;
   struct vrend_interp_info interpinfo[PIPE_MAX_SHADER_INPUTS];
};

struct vrend_shader_info_out {
   uint64_t num_indirect_generic : 8;
   uint64_t num_indirect_patch : 8;
   uint64_t num_generic_and_patch : 8;
   uint64_t guest_sent_io_arrays : 1;
};

struct vrend_shader_info_in {
   uint64_t generic_emitted_mask;
   uint32_t num_indirect_generic : 8;
   uint32_t num_indirect_patch : 8;
   uint32_t use_pervertex : 1;
};


struct vrend_shader_info {
   uint64_t invariant_outputs;
   struct vrend_shader_info_out out;
   struct vrend_shader_info_in in;

   struct vrend_layout_info generic_outputs_layout[64];
   struct vrend_array *sampler_arrays;
   struct vrend_array *image_arrays;
   char **so_names;
   struct pipe_stream_output_info so_info;

   uint32_t samplers_used_mask;
   uint32_t images_used_mask;
   uint32_t ubo_used_mask;
   uint32_t ssbo_used_mask;
   uint32_t shadow_samp_mask;
   uint32_t attrib_input_mask;
   uint32_t fs_blend_equation_advanced;
   uint32_t fog_input_mask;
   uint32_t fog_output_mask;

   int num_consts;
   int num_inputs;
   int num_outputs;
   int gs_out_prim;
   int tes_prim;
   int num_sampler_arrays;
   int num_image_arrays;

   uint8_t ubo_indirect : 1;
   uint8_t tes_point_mode : 1;
   uint8_t gles_use_tex_query_level : 1;
};

struct vrend_variable_shader_info {
   struct vrend_fs_shader_info fs_info;
   int num_ucp;
   int num_clip;
   int num_cull;
};

struct vrend_shader_key {
   uint64_t force_invariant_inputs;

   struct vrend_fs_shader_info *fs_info;
   struct vrend_shader_info_out input;
   struct vrend_shader_info_in output;
   struct vrend_layout_info prev_stage_generic_and_patch_outputs_layout[64];

   union {
      struct {
         uint8_t surface_component_bits[PIPE_MAX_COLOR_BUFS];
         uint32_t coord_replace;
         uint8_t swizzle_output_rgb_to_bgr;
         uint8_t convert_linear_to_srgb_on_write;
         uint8_t cbufs_are_a8_bitmask;
         uint8_t cbufs_signed_int_bitmask;
         uint8_t cbufs_unsigned_int_bitmask;
         uint32_t logicop_func : 4;
         uint32_t logicop_enabled : 1;
         uint32_t prim_is_points : 1;
         uint32_t invert_origin : 1;
      } fs;

      struct {
         uint32_t attrib_signed_int_bitmask;
         uint32_t attrib_unsigned_int_bitmask;
         uint32_t fog_fixup_mask;
      } vs;
   };

   uint32_t compiled_fs_uid;

   uint8_t alpha_test;
   uint8_t clip_plane_enable;
   uint8_t num_cull : 4;
   uint8_t num_clip : 4;
   uint8_t pstipple_tex : 1;
   uint8_t add_alpha_test : 1;
   uint8_t color_two_side : 1;
   uint8_t gs_present : 1;
   uint8_t tcs_present : 1;
   uint8_t tes_present : 1;
   uint8_t flatshade : 1;

};

struct vrend_shader_cfg {
   uint32_t glsl_version : 12;
   uint32_t max_draw_buffers : 4;
   uint32_t use_gles : 1;
   uint32_t use_core_profile : 1;
   uint32_t use_explicit_locations : 1;
   uint32_t has_arrays_of_arrays : 1;
   uint32_t has_gpu_shader5 : 1;
   uint32_t has_es31_compat : 1;
   uint32_t has_conservative_depth : 1;
   uint32_t use_integer : 1;
   uint32_t has_dual_src_blend : 1;
   uint32_t has_fbfetch_coherent : 1;
};

struct vrend_context;

#define SHADER_MAX_STRINGS 3
#define SHADER_STRING_VER_EXT 0
#define SHADER_STRING_HDR 1

bool vrend_convert_shader(const struct vrend_context *rctx,
                          const struct vrend_shader_cfg *cfg,
                          const struct tgsi_token *tokens,
                          uint32_t req_local_mem,
                          const struct vrend_shader_key *key,
                          struct vrend_shader_info *sinfo,
                          struct vrend_variable_shader_info *var_sinfo,
                          struct vrend_strarray *shader);

const char *vrend_shader_samplertypeconv(bool use_gles, int sampler_type);

char vrend_shader_samplerreturnconv(enum tgsi_return_type type);

int vrend_shader_lookup_sampler_array(const struct vrend_shader_info *sinfo, int index);

bool vrend_shader_create_passthrough_tcs(const struct vrend_context *ctx,
                                         const struct vrend_shader_cfg *cfg,
                                         const struct tgsi_token *vs_info,
                                         const struct vrend_shader_key *key,
                                         const float tess_factors[6],
                                         struct vrend_shader_info *sinfo,
                                         struct vrend_strarray *shader,
                                         int vertices_per_patch);

bool vrend_shader_needs_alpha_func(const struct vrend_shader_key *key);

#endif
