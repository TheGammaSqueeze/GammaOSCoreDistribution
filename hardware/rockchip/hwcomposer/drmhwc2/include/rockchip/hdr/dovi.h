#ifndef DOVI_DEFINE_H
#define DOVI_DEFINE_H

#ifdef __cplusplus
extern "C" {
#endif

#define MAX_NUM_INPUT 4

enum
{
    DOVI_CORE1_VALID = 1,
    DOVI_CORE2_VALID = 2,
    DOVI_CORE3_VALID = 4,
};

enum
{
    DOVI_INPUT_MODE_OTT = 0,
    DOVI_INPUT_MODE_HDMI,
    DOVI_INPUT_MODE_GFX
};

enum
{
    DOVI_PRIORITY_GRAPHICS = 0,
    DOVI_PRIORITY_VIDEO,
};

typedef enum dovi_format
{
    DOVI_FORMAT_INVALID = -1,
    DOVI_FORMAT_DOVI = 0,
    DOVI_FORMAT_HDR10 = 1,
    DOVI_FORMAT_SDR8 = 2,
    DOVI_FORMAT_SDR10 = 3,
    DOVI_FORMAT_HLG = 4,
    DOVI_FORMAT_HDR8 = 5,
} dovi_format_e;

typedef struct dovi_hdr_blob {
    unsigned int hdr_type;
    unsigned int length;
    char regs[12412];
} dovi_hdr_blob_s;

typedef struct dovi_input {
    unsigned int width;
    unsigned int height;
    unsigned int frame_rate;
    dovi_format_e format;
    unsigned int mode;
    unsigned char *payload;
    unsigned int payload_size;
} dovi_input_s;

typedef struct dovi_cfg_input {
    dovi_input_s input[MAX_NUM_INPUT];
    uint32_t pri_input;
    uint32_t num_input;
    // required when output mode is DOVI
    unsigned char *vsvdb_buf;
} dovi_cfg_input_s;

typedef struct dovi_cfg_output {
    unsigned int eotf;
    dovi_format_e format; // output mode, sdr/hdr10/hlg/dovi
    unsigned int priority_mode;
    int vpm_trans_timeout;
    unsigned int user_l11;
    uint8_t user_l11_buf[4];
} dovi_cfg_output_s;

enum
{
    DOVI_INFOFRAME_SDP,  // Using DP SDP packet
    DOVI_INFOFRAME_VSIF, // Using VSIF packet
    DOVI_INFOFRAME_VSEM, // Embedded in data
};

typedef struct dovi_infoframe {
    unsigned int type;
    unsigned int size;
    unsigned char data[0x1000];
} dovi_infoframe_s;

struct hdr_static_metadata_infoframe {
    uint8_t eotf;
    uint8_t metadata_type;
    struct {
        uint16_t x, y;
    } display_primaries[3];
    struct {
        uint16_t x, y;
    } white_point;
    uint16_t max_display_mastering_luminance;
    uint16_t min_display_mastering_luminance;
    uint16_t max_cll;
    uint16_t max_fall;
};

struct hdr_staic_metadata {
    uint32_t metadata_type;
    union {
        struct hdr_static_metadata_infoframe hdmi_metadata_type1;
    };
};

typedef struct dovi_parser_param {
    dovi_cfg_input_s input_cfg;   // Input dovi video and gfx info
    dovi_cfg_output_s output_cfg; // Output configuration
    /* Generated regs info */
    dovi_hdr_blob_s blob; // Generated dovi core regs and luts
    /* Generated hdmi static metadata */
    struct hdr_staic_metadata hdr_metadata; // Required when output is HDR10/HLG
    /* Generated hdmi dovi vsif */
    dovi_infoframe_s dovi_infoframe; // Required when output is DOVI
} dovi_parser_param_s;

typedef void *dovi_handle_t;

dovi_handle_t dovi_init(void);
int dovi_parser(dovi_handle_t handle, dovi_parser_param_s *param);
void dovi_deinit(dovi_handle_t handle);

#ifdef __cplusplus
}
#endif

#endif
