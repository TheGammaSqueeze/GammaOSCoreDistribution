#ifndef _LIBHDRPARSER_H
#define _LIBHDRPARSER_H

#define     RK_HDRVIVID_TONE_SCA_TAB_LENGTH     257
#define     RK_HDRVIVID_GAMMA_CURVE_LENGTH      81
#define     RK_HDRVIVID_GAMMA_MDFVALUE_LENGTH   9
#define     RK_SDR2HDR_INVGAMMA_CURVE_LENGTH    69
#define     RK_SDR2HDR_INVGAMMA_S_IDX_LENGTH    6
#define     RK_SDR2HDR_INVGAMMA_C_IDX_LENGTH    6
#define     RK_SDR2HDR_SMGAIN_LENGTH            64

#define     RK_HDRVIVID_TONE_SCA_AXI_TAB_LENGTH 264

#define     RK_MAGIC_WORD 318
enum rk_video_format
{
	AVS2 = 0,
	HEVC = 1,
    H264 = 2,
	VIDEO_FORMAT_MAX
};

enum rk_hdr_format
{
	  NONE = 0,
    HDR10 = 1,
    HLGSTATIC = 2,
//	RESERVED3 = 3, //reserved for more future static hdr format
//	RESERVED4 = 4, //reserved for more future static hdr format
    HDRVIVID = 5,
//	RESERVED6 = 6, //reserved for hdr vivid
//	RESERVED7 = 7, //reserved for hdr vivid
    HDR10PLUS = 8,
//	RESERVED9 = 9, //reserved for hdr10+
//	RESERVED10 = 10,//reserved for hdr10+
    DOLBY = 11,
//	RESERVED12 = 12, //reserved for other dynamic hdr format
//	RESERVED13 = 13, //reserved for  other dynamic hdr format
    HDR_FORMAT_MAX
};


enum rk_hdr_payload_format
{
    STATIC = 0,
    DYNAMIC = 1,
    HDR_PAYLOAD_FORMAT_MAX
};

//SinkStaticMetaEOTF
enum rk_hdr_eotf
{
    SINK_EOTF_GAMMA_SDR = 0,
    SINK_EOTF_GAMMA_HDR = 1,
    SINK_EOTF_ST2084 = 2,
    SINK_EOTF_HLG = 3,
    SINK_EOTF_RESERVED4 = 4,
    SINK_EOTF_RESERVED5 = 5,
    SINK_EOTF_UNSPECIFIED
};

enum rk_hdr_color_prim
{
	COLOR_PRIM_BT709 = 0,
	COLOR_PRIM_BT2020 = 1,
	COLOR_PRIM_RESERVED2 = 2,
	COLOR_PRIM_RESERVED3 = 3,
	COLOR_PRIM_UNSPECIFIED
};

enum rk_hdr_range
{
	RANGE_FULL = 0,
	RANGE_LIMITED = 1,
	RANGE_UNSPECIFIED
};

typedef struct {
	unsigned int color_prim; //enum rk_hdr_color_prim: bt709, bt2020, etc.
	unsigned int eotf; //enum rk_hdr_eotf: sdr, st2084, hlg, etc.
	unsigned int range; //enum rk_hdr_range: full, limit, etc.
}rk_hdr_dataspace_info_t;

typedef struct{
    unsigned char print_input_meta;
    unsigned char hdr_log_level;
}rk_hdr_parser_debug_t;

/**
* hdr static metadata from codec
**/
typedef struct {
	unsigned int    color_space;
	unsigned int    color_primaries;
	unsigned int    color_trc;
	unsigned int    red_x;
	unsigned int    red_y;
	unsigned int    green_x;
	unsigned int    green_y;
	unsigned int    blue_x;
	unsigned int    blue_y;
	unsigned int    white_point_x;
	unsigned int    white_point_y;
	unsigned int    min_luminance;
	unsigned int    max_luminance;
	unsigned int    max_cll;
	unsigned int    max_fall;
	unsigned int	reserved[4];
}rk_hdr_static_meta_t;

typedef struct {
	unsigned short  hdr_format;         /* HDR protocol: HDR10, HLG, Dolby, HDRVivid ...    */
	unsigned short  video_format;       /* video format: H.264, H.265, AVS2 ...         */
	rk_hdr_static_meta_t static_meta;	/* static metadata from codec*/
}rk_hdr_fmt_info_t;

typedef struct{
	unsigned int            color_prim     ; //enum rk_hdr_color_prim: bt709, bt2020, etc.
    unsigned int        	eotf		   ; //enum rk_hdr_eotf: sdr, st2084, hlg, etc.
    unsigned int            red_x          ;
	unsigned int            red_y          ;
	unsigned int            green_x        ;
	unsigned int            green_y        ;
	unsigned int            blue_x         ;
	unsigned int            blue_y         ;
    unsigned int            white_point_x  ;
    unsigned int            white_point_y  ;
	unsigned int            dst_min        ; //min_display_luminance(nits) * 100
	unsigned int            dst_max        ; //max_display_luminance(nits) * 100
}rk_target_display_data_t;

/**
 * struct hdr_metadata_infoframe - HDR Metadata Infoframe Data.
 *
 * HDR Metadata Infoframe as per CTA 861.G spec. This is expected
 * to match exactly with the spec.
 *
 * Userspace is expected to pass the metadata information as per
 * the format described in this structure.
 */
struct rk_hdr_metadata_infoframe {
	/**
	 * @eotf: Electro-Optical Transfer Function (EOTF)
	 * used in the stream.
	 */
	unsigned char eotf; // 0: SDR-Gamma, 1: HDR-Gamma, 2: SMPTE2084, 3: HLG
	/**
	 * @metadata_type: Static_Metadata_Descriptor_ID.
	 */
	unsigned char metadata_type;
	/**
	 * @display_primaries: Color Primaries of the Data.
	 * These are coded as unsigned 16-bit values in units of
	 * 0.00002, where 0x0000 represents zero and 0xC350
	 * represents 1.0000.
	 * @display_primaries.x: X cordinate of color primary.
	 * @display_primaries.y: Y cordinate of color primary.
	 */
	struct {
		unsigned short x, y;
		} display_primaries[3];
	/**
	 * @white_point: White Point of Colorspace Data.
	 * These are coded as unsigned 16-bit values in units of
	 * 0.00002, where 0x0000 represents zero and 0xC350
	 * represents 1.0000.
	 * @white_point.x: X cordinate of whitepoint of color primary.
	 * @white_point.y: Y cordinate of whitepoint of color primary.
	 */
	struct {
		unsigned short x, y;
		} white_point;
	/**
	 * @max_display_mastering_luminance: Max Mastering Display Luminance.
	 * This value is coded as an unsigned 16-bit value in units of 1 cd/m2,
	 * where 0x0001 represents 1 cd/m2 and 0xFFFF represents 65535 cd/m2.
	 */
	unsigned short max_display_mastering_luminance;
	/**
	 * @min_display_mastering_luminance: Min Mastering Display Luminance.
	 * This value is coded as an unsigned 16-bit value in units of
	 * 0.0001 cd/m2, where 0x0001 represents 0.0001 cd/m2 and 0xFFFF
	 * represents 6.5535 cd/m2.
	 */
	unsigned short min_display_mastering_luminance;
	/**
	 * @max_cll: Max Content Light Level.
	 * This value is coded as an unsigned 16-bit value in units of 1 cd/m2,
	 * where 0x0001 represents 1 cd/m2 and 0xFFFF represents 65535 cd/m2.
	 */
	unsigned short max_cll;
	/**
	 * @max_fall: Max Frame Average Light Level.
	 * This value is coded as an unsigned 16-bit value in units of 1 cd/m2,
	 * where 0x0001 represents 1 cd/m2 and 0xFFFF represents 65535 cd/m2.
	 */
	unsigned short max_fall;
};

/**
 * struct hdr_output_metadata - HDR output metadata
 *
 * Metadata Information to be passed from userspace
 */
typedef struct {
	/**
	 * @metadata_type: Static_Metadata_Descriptor_ID.
	 */
	unsigned int metadata_type;
	/**
	 * @hdmi_metadata_type1: HDR Metadata Infoframe.
	 */
	union {
		struct rk_hdr_metadata_infoframe hdmi_metadata_type1;
	};
}rk_hdr_output_hdmi_metadata_t;



/*
 * HDR metadata format from codec
 *
 *  +----------+
 *  |  header1 |
 *  +----------+
 *  |          |
 *  |  payload |
 *  |          |
 *  +----------+
 *  |  header2 |
 *  +----------+
 *  |          |
 *  |  payload |
 *  |          |
 *  +----------+
 *  |  header3 |
 *  +----------+
 *  |          |
 *  |  payload |
 *  |          |
 *  +----------+
 */
typedef struct RkMetaHdrHeader_t {
    /* For transmission */
    unsigned short  magic;               /* magic word for checking overwrite error      */
    unsigned short  size;               /* total header+payload length including header */
    unsigned short  message_total;      /* total message count in current transmission  */
    unsigned short  message_index;      /* current message index in the transmission    */

    /* For payload identification */
    unsigned short  version;            /* payload structure version                    */
    unsigned short  hdr_format;         /* HDR protocol: HDR10, HLG, Dolby, HDRVivid ...    */
    unsigned short  hdr_payload_type;   /* HDR data type: static data, dynamic data ... */
    unsigned short  video_format;       /* video format: H.264, H.265, AVS2 ...         */

    /* For extenstion usage */
    unsigned int  reserve[4];

    /* payload data aligned to 32bits */
    unsigned int  payload[];
} RkMetaHdrHeader;


typedef struct{
    rk_hdr_parser_debug_t hdr_debug_cfg;

    // unsigned char   display_mode;       // 0-Auto, 1-HDR10, 2-SDR
    unsigned char   hdr_pq_max_y_mode;  // PQ-Luma Mode: 0-Max, 1-calcY
    float           hdr_dst_gamma;      // Default 2.2

    float           s2h_sm_ratio;       // S2h Sat Modify, Default 1.0, range [0.5, 1.5]
    float           s2h_scale_ratio;    // S2h Luma Scalling Ratio, Default 1.0, range [0.5, 1.5]
    unsigned char   s2h_sdr_color_space;// S2h Color Space 0: bt601-NTSC525, 1: bt601-PAL625, 2: bt709, Default 2
}rk_hdr_user_cfg_t;

typedef struct{
	// Header
	// Hdr protocol (enum rk_hdr_format NONE, HDR10, HLGSTATIC, HDRVIVID, etc.)
	unsigned int hdr_type;
	// Payload length of HdrVivid Register
	unsigned int length;

	// Payload
    // Params for HDR and Sdr2hdr Hardware Register
    unsigned int sdr2hdr_ctrl;
    unsigned int sdr2hdr_coe0;
    unsigned int sdr2hdr_coe1;
    unsigned int sdr2hdr_csc_coe00_01;
    unsigned int sdr2hdr_csc_coe02_10;
    unsigned int sdr2hdr_csc_coe11_12;
    unsigned int sdr2hdr_csc_coe20_21;
    unsigned int sdr2hdr_csc_coe22;
    unsigned int hdrvivid_ctrl;
    unsigned int hdr_pq_gamma;
    unsigned int hlg_rfix_scalefac;
    unsigned int hlg_maxluma;
    unsigned int hlg_r_tm_lin2non;
    unsigned int hdr_csc_coe00_01;
    unsigned int hdr_csc_coe02_10;
    unsigned int hdr_csc_coe11_12;
    unsigned int hdr_csc_coe20_21;
    unsigned int hdr_csc_coe22;
    unsigned int hdr_tone_sca[RK_HDRVIVID_TONE_SCA_TAB_LENGTH];
    unsigned int hdrgamma_curve[RK_HDRVIVID_GAMMA_CURVE_LENGTH];
    unsigned int hdrgamma_mdfvalue[RK_HDRVIVID_GAMMA_MDFVALUE_LENGTH];
    unsigned int sdrinvgamma_curve[RK_SDR2HDR_INVGAMMA_CURVE_LENGTH];
    unsigned int sdrinvgamma_startidx[RK_SDR2HDR_INVGAMMA_S_IDX_LENGTH];
    unsigned int sdrinvgamma_changeidx[RK_SDR2HDR_INVGAMMA_C_IDX_LENGTH];
    unsigned int sdr_smgain[RK_SDR2HDR_SMGAIN_LENGTH];

    // HDR-Mode
    unsigned char hdr_mode; // 0-5: Mode0-5, 6: bypass, 7: Hdr10 to Sdr

    // AXI-Tab
    unsigned int tone_sca_axi_tab[RK_HDRVIVID_TONE_SCA_AXI_TAB_LENGTH];
}rk_hdr_reg_t;

typedef struct{
    bool                    		codec_meta_exist;   // [i] hdr metadata exist: 0: hdr_meta not exist, 1: hdr_meta exist
    RkMetaHdrHeader*                p_hdr_codec_meta;   // [i] hdr metadata from codec
    rk_hdr_dataspace_info_t			hdr_dataspace_info;	// [i] hdr dataspace info from Android dataspace
    rk_target_display_data_t  		hdr_hdmi_meta;   	// [i] target display data from hdmi edid or manual settings
    rk_hdr_user_cfg_t         		hdr_user_cfg;    	// [i] user config for debugging & modifying built-in adjustable effects
    rk_hdr_reg_t                    hdr_reg;            // [o] hdr register configuration for driver
    rk_hdr_output_hdmi_metadata_t   target_display_data;// [o] static metadata sent to display according to CTA 861.G spec
}rk_hdr_parser_params_t;

int hdr_format_parser(rk_hdr_parser_params_t* p_hdr_parser_params, rk_hdr_fmt_info_t* p_hdr_fmt_info);

int hdr_parser(rk_hdr_parser_params_t* p_hdr_parser_params);

#endif
