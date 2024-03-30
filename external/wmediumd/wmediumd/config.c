/*
 *	wmediumd, wireless medium simulator for mac80211_hwsim kernel module
 *	Copyright (c) 2011 cozybit Inc.
 *	Copyright (C) 2020 Intel Corporation
 *
 *	Author: Javier Lopez    <jlopex@cozybit.com>
 *		Javier Cardona  <javier@cozybit.com>
 *
 *	This program is free software; you can redistribute it and/or
 *	modify it under the terms of the GNU General Public License
 *	as published by the Free Software Foundation; either version 2
 *	of the License, or (at your option) any later version.
 *
 *	This program is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *	GNU General Public License for more details.
 *
 *	You should have received a copy of the GNU General Public License
 *	along with this program; if not, write to the Free Software
 *	Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 *	02110-1301, USA.
 */

#include <sys/timerfd.h>
#include <libconfig.h>
#include <string.h>
#include <stdlib.h>
#include <errno.h>
#include <math.h>

#include "wmediumd.h"
#include "config.h"

static void string_to_mac_address(const char *str, u8 *addr)
{
	int a[ETH_ALEN];

	sscanf(str, "%x:%x:%x:%x:%x:%x",
	       &a[0], &a[1], &a[2], &a[3], &a[4], &a[5]);

	addr[0] = (u8) a[0];
	addr[1] = (u8) a[1];
	addr[2] = (u8) a[2];
	addr[3] = (u8) a[3];
	addr[4] = (u8) a[4];
	addr[5] = (u8) a[5];
}

static int get_link_snr_default(struct wmediumd *ctx, struct station *sender,
				 struct station *receiver)
{
	return SNR_DEFAULT;
}

static int get_link_snr_from_snr_matrix(struct wmediumd *ctx,
					struct station *sender,
					struct station *receiver)
{
	return ctx->snr_matrix[sender->index * ctx->num_stas + receiver->index];
}

static double _get_error_prob_from_snr(struct wmediumd *ctx, double snr,
				       unsigned int rate_idx, u32 freq,
				       int frame_len,
				       struct station *src, struct station *dst)
{
	return get_error_prob_from_snr(snr, rate_idx, freq, frame_len);
}

static double get_error_prob_from_matrix(struct wmediumd *ctx, double snr,
					 unsigned int rate_idx, u32 freq,
					 int frame_len, struct station *src,
					 struct station *dst)
{
	if (dst == NULL) // dst is multicast. returned value will not be used.
		return 0.0;

	return ctx->error_prob_matrix[ctx->num_stas * src->index + dst->index];
}

int use_fixed_random_value(struct wmediumd *ctx)
{
	return ctx->error_prob_matrix != NULL;
}

#define FREQ_1CH (2.412e9)		// [Hz]
#define SPEED_LIGHT (2.99792458e8)	// [meter/sec]
/*
 * Calculate path loss based on a free-space path loss
 *
 * This function returns path loss [dBm].
 */
static int calc_path_loss_free_space(void *model_param,
			  struct station *dst, struct station *src)
{
	double PL, d;

	d = sqrt((src->x - dst->x) * (src->x - dst->x) +
		 (src->y - dst->y) * (src->y - dst->y));

	/*
	 * Calculate PL0 with Free-space path loss in decibels
	 *
	 * 20 * log10 * (4 * M_PI * d * f / c)
	 *   d: distance [meter]
	 *   f: frequency [Hz]
	 *   c: speed of light in a vacuum [meter/second]
	 *
	 * https://en.wikipedia.org/wiki/Free-space_path_loss
	 */
	PL = 20.0 * log10(4.0 * M_PI * d * FREQ_1CH / SPEED_LIGHT);
	return PL;
}
/*
 * Calculate path loss based on a log distance model
 *
 * This function returns path loss [dBm].
 */
static int calc_path_loss_log_distance(void *model_param,
			  struct station *dst, struct station *src)
{
	struct log_distance_model_param *param;
	double PL, PL0, d;

	param = model_param;

	d = sqrt((src->x - dst->x) * (src->x - dst->x) +
		 (src->y - dst->y) * (src->y - dst->y));

	/*
	 * Calculate PL0 with Free-space path loss in decibels
	 *
	 * 20 * log10 * (4 * M_PI * d * f / c)
	 *   d: distance [meter]
	 *   f: frequency [Hz]
	 *   c: speed of light in a vacuum [meter/second]
	 *
	 * https://en.wikipedia.org/wiki/Free-space_path_loss
	 */
	PL0 = 20.0 * log10(4.0 * M_PI * 1.0 * FREQ_1CH / SPEED_LIGHT);

	/*
	 * Calculate signal strength with Log-distance path loss model
	 * https://en.wikipedia.org/wiki/Log-distance_path_loss_model
	 */
	PL = PL0 + 10.0 * param->path_loss_exponent * log10(d) + param->Xg;

	return PL;
}
/*
 * Calculate path loss based on a itu model
 *
 * This function returns path loss [dBm].
 */
static int calc_path_loss_itu(void *model_param,
			  struct station *dst, struct station *src)
{
	struct itu_model_param *param;
	double PL, d;
	int N=28;

	param = model_param;

	d = sqrt((src->x - dst->x) * (src->x - dst->x) +
		 (src->y - dst->y) * (src->y - dst->y));

	if (d>16)
		N=38;
	/*
	 * Calculate signal strength with ITU path loss model
	 * Power Loss Coefficient Based on the Paper
         * Site-Specific Validation of ITU Indoor Path Loss Model at 2.4 GHz
         * from Theofilos Chrysikos, Giannis Georgopoulos and Stavros Kotsopoulos
	 * LF: floor penetration loss factor
	 * nFLOORS: number of floors
	 */
	PL = 20.0 * log10(FREQ_1CH) + N * log10(d) + param->LF * param->nFLOORS - 28;
	return PL;
}

static void recalc_path_loss(struct wmediumd *ctx)
{
	int start, end, path_loss;

	for (start = 0; start < ctx->num_stas; start++) {
		for (end = 0; end < ctx->num_stas; end++) {
			if (start == end)
				continue;

			path_loss = ctx->calc_path_loss(ctx->path_loss_param,
				ctx->sta_array[end], ctx->sta_array[start]);
			ctx->snr_matrix[ctx->num_stas * start + end] =
				ctx->sta_array[start]->tx_power - path_loss -
				NOISE_LEVEL;
		}
	}
}

static void move_stations_to_direction(struct usfstl_job *job)
{
	struct wmediumd *ctx = job->data;
	struct station *station;

	list_for_each_entry(station, &ctx->stations, list) {
		station->x += station->dir_x;
		station->y += station->dir_y;
	}
	recalc_path_loss(ctx);

	job->start += MOVE_INTERVAL * 1000000;
	usfstl_sched_add_job(&scheduler, job);
}

static int parse_path_loss(struct wmediumd *ctx, config_t *cf)
{
	struct station *station;
	const config_setting_t *positions, *position;
	const config_setting_t *directions, *direction;
	const config_setting_t *tx_powers, *model;
	const char *path_loss_model_name;

	positions = config_lookup(cf, "model.positions");
	if (!positions) {
		w_flogf(ctx, LOG_ERR, stderr,
			"No positions found in model\n");
		return -EINVAL;
	}
	if (config_setting_length(positions) != ctx->num_stas) {
		w_flogf(ctx, LOG_ERR, stderr,
			"Specify %d positions\n", ctx->num_stas);
		return -EINVAL;
	}

	directions = config_lookup(cf, "model.directions");
	if (directions) {
		if (config_setting_length(directions) != ctx->num_stas) {
			w_flogf(ctx, LOG_ERR, stderr,
				"Specify %d directions\n", ctx->num_stas);
			return -EINVAL;
		}
		ctx->move_job.start = MOVE_INTERVAL * 1000000;
		ctx->move_job.name = "move";
		ctx->move_job.data = ctx;
		ctx->move_job.callback = move_stations_to_direction;
		usfstl_sched_add_job(&scheduler, &ctx->move_job);
	}

	tx_powers = config_lookup(cf, "model.tx_powers");
	if (!tx_powers) {
		w_flogf(ctx, LOG_ERR, stderr,
			"No tx_powers found in model\n");
		return -EINVAL;
	}
	if (config_setting_length(tx_powers) != ctx->num_stas) {
		w_flogf(ctx, LOG_ERR, stderr,
			"Specify %d tx_powers\n", ctx->num_stas);
		return -EINVAL;
	}

	model = config_lookup(cf, "model");
	if (config_setting_lookup_string(model, "model_name",
		&path_loss_model_name) != CONFIG_TRUE) {
		w_flogf(ctx, LOG_ERR, stderr, "Specify model_name\n");
		return -EINVAL;
	}
	if (strncmp(path_loss_model_name, "log_distance",
		    sizeof("log_distance")) == 0) {
		struct log_distance_model_param *param;

		ctx->calc_path_loss = calc_path_loss_log_distance;

		param = malloc(sizeof(*param));
		if (!param) {
			w_flogf(ctx, LOG_ERR, stderr,
				"Out of memory(path_loss_param)\n");
			return -EINVAL;
		}

		if (config_setting_lookup_float(model, "path_loss_exp",
			&param->path_loss_exponent) != CONFIG_TRUE) {
			w_flogf(ctx, LOG_ERR, stderr,
				"path_loss_exponent not found\n");
			return -EINVAL;
		}

		if (config_setting_lookup_float(model, "xg",
			&param->Xg) != CONFIG_TRUE) {
			w_flogf(ctx, LOG_ERR, stderr, "xg not found\n");
			return -EINVAL;
		}
		ctx->path_loss_param = param;
	}
	else if (strncmp(path_loss_model_name, "free_space",
			sizeof("free_space")) == 0) {
		struct log_distance_model_param *param;
		ctx->calc_path_loss = calc_path_loss_free_space;
		param = malloc(sizeof(*param));
		if (!param) {
			w_flogf(ctx, LOG_ERR, stderr,
				"Out of memory(path_loss_param)\n");
			return -EINVAL;
		}
	}
	else if (strncmp(path_loss_model_name, "itu",
			sizeof("itu")) == 0) {
		struct itu_model_param *param;
		ctx->calc_path_loss = calc_path_loss_itu;
		param = malloc(sizeof(*param));
		if (!param) {
			w_flogf(ctx, LOG_ERR, stderr,
				"Out of memory(path_loss_param)\n");
			return -EINVAL;
		}

		if (config_setting_lookup_int(model, "nFLOORS",
			&param->nFLOORS) != CONFIG_TRUE) {
			w_flogf(ctx, LOG_ERR, stderr,
				"nFLOORS not found\n");
			return -EINVAL;
		}

		if (config_setting_lookup_int(model, "LF",
			&param->LF) != CONFIG_TRUE) {
			w_flogf(ctx, LOG_ERR, stderr, "LF not found\n");
			return -EINVAL;
		}
		ctx->path_loss_param = param;
	}
	else {
		w_flogf(ctx, LOG_ERR, stderr, "No path loss model found\n");
		return -EINVAL;
	}

	list_for_each_entry(station, &ctx->stations, list) {
		position = config_setting_get_elem(positions, station->index);
		if (config_setting_length(position) != 2) {
			w_flogf(ctx, LOG_ERR, stderr,
				"Invalid position: expected (double,double)\n");
			return -EINVAL;
		}
		station->x = config_setting_get_float_elem(position, 0);
		station->y = config_setting_get_float_elem(position, 1);

		if (directions) {
			direction = config_setting_get_elem(directions,
				station->index);
			if (config_setting_length(direction) != 2) {
				w_flogf(ctx, LOG_ERR, stderr,
					"Invalid direction: expected (double,double)\n");
				return -EINVAL;
			}
			station->dir_x = config_setting_get_float_elem(
				direction, 0);
			station->dir_y = config_setting_get_float_elem(
				direction, 1);
		}

		station->tx_power = config_setting_get_float_elem(
			tx_powers, station->index);
	}

	recalc_path_loss(ctx);

	return 0;
}

static double pseudo_normal_distribution(void)
{
	int i;
	double normal = -6.0;

	for (i = 0; i < 12; i++)
		normal += drand48();

	return normal;
}

static int _get_fading_signal(struct wmediumd *ctx)
{
	return ctx->fading_coefficient * pseudo_normal_distribution();
}

static int get_no_fading_signal(struct wmediumd *ctx)
{
	return 0;
}

/* Existing link is from from -> to; copy to other dir */
static void mirror_link(struct wmediumd *ctx, int from, int to)
{
	ctx->snr_matrix[ctx->num_stas * to + from] =
		ctx->snr_matrix[ctx->num_stas * from + to];

	if (ctx->error_prob_matrix) {
		ctx->error_prob_matrix[ctx->num_stas * to + from] =
			ctx->error_prob_matrix[ctx->num_stas * from + to];
	}
}

int validate_config(const char* file) {
	struct wmediumd ctx = {};

	INIT_LIST_HEAD(&ctx.stations);
	INIT_LIST_HEAD(&ctx.clients);
	INIT_LIST_HEAD(&ctx.clients_to_free);

	int load_result = load_config(&ctx, file, NULL);

	clear_config(&ctx);

	if (load_result < 0) return 0;

	return 1;
}

/*
 *	Loads a config file into memory
 */
int load_config(struct wmediumd *ctx, const char *file, const char *per_file)
{
	config_t cfg, *cf;
	const config_setting_t *ids, *links, *model_type;
	const config_setting_t *error_probs = NULL, *error_prob;
	const config_setting_t *enable_interference;
	const config_setting_t *fading_coefficient, *default_prob;
	int count_ids, i, j;
	int start, end, snr;
	struct station *station;
	const char *model_type_str;
	float default_prob_value = 0.0;
	bool *link_map = NULL;

	ctx->config_path = strdup(file);

	/*initialize the config file*/
	cf = &cfg;
	config_init(cf);

	/*read the file*/
	if (!config_read_file(cf, file)) {
		w_logf(ctx, LOG_ERR, "Error loading file %s at line:%d, reason: %s\n",
				file,
				config_error_line(cf),
				config_error_text(cf));
		config_destroy(cf);
		return -EIO;
	}

	ids = config_lookup(cf, "ifaces.ids");
	if (!ids) {
		w_logf(ctx, LOG_ERR, "ids not found in config file\n");
		return -EIO;
	}
	count_ids = config_setting_length(ids);

	w_logf(ctx, LOG_NOTICE, "#_if = %d\n", count_ids);

	/* Fill the mac_addr */
	ctx->sta_array = calloc(count_ids, sizeof(struct station *));
	if (!ctx->sta_array) {
		w_flogf(ctx, LOG_ERR, stderr, "Out of memory(sta_array)!\n");
		return -ENOMEM;
	}
	for (i = 0; i < count_ids; i++) {
		u8 addr[ETH_ALEN];
		const char *str =  config_setting_get_string_elem(ids, i);
		string_to_mac_address(str, addr);

		station = calloc(1, sizeof(*station));
		if (!station) {
			w_flogf(ctx, LOG_ERR, stderr, "Out of memory!\n");
			return -ENOMEM;
		}
		station->index = i;
		memcpy(station->addr, addr, ETH_ALEN);
		memcpy(station->hwaddr, addr, ETH_ALEN);
		station->tx_power = SNR_DEFAULT;
		station_init_queues(station);
		list_add_tail(&station->list, &ctx->stations);
		ctx->sta_array[i] = station;

		w_logf(ctx, LOG_NOTICE, "Added station %d: " MAC_FMT "\n", i, MAC_ARGS(addr));
	}
	ctx->num_stas = count_ids;

	enable_interference = config_lookup(cf, "ifaces.enable_interference");
	if (enable_interference &&
	    config_setting_get_bool(enable_interference)) {
		ctx->intf = calloc(ctx->num_stas * ctx->num_stas,
				   sizeof(struct intf_info));
		if (!ctx->intf) {
			w_flogf(ctx, LOG_ERR, stderr, "Out of memory(intf)\n");
			return -ENOMEM;
		}
		for (i = 0; i < ctx->num_stas; i++)
			for (j = 0; j < ctx->num_stas; j++)
				ctx->intf[i * ctx->num_stas + j].signal = -200;
	} else {
		ctx->intf = NULL;
	}

	fading_coefficient =
		config_lookup(cf, "model.fading_coefficient");
	if (fading_coefficient &&
	    config_setting_get_int(fading_coefficient) > 0) {
		ctx->get_fading_signal = _get_fading_signal;
		ctx->fading_coefficient =
			config_setting_get_int(fading_coefficient);
	} else {
		ctx->get_fading_signal = get_no_fading_signal;
		ctx->fading_coefficient = 0;
	}

	/* create link quality matrix */
	ctx->snr_matrix = calloc(sizeof(int), count_ids * count_ids);
	if (!ctx->snr_matrix) {
		w_flogf(ctx, LOG_ERR, stderr, "Out of memory!\n");
		return -ENOMEM;
	}
	/* set default snrs */
	for (i = 0; i < count_ids * count_ids; i++)
		ctx->snr_matrix[i] = SNR_DEFAULT;

	links = config_lookup(cf, "ifaces.links");
	if (!links) {
		model_type = config_lookup(cf, "model.type");
		if (model_type) {
			model_type_str = config_setting_get_string(model_type);
			if (memcmp("snr", model_type_str, strlen("snr")) == 0) {
				links = config_lookup(cf, "model.links");
			} else if (memcmp("prob", model_type_str,
				strlen("prob")) == 0) {
				error_probs = config_lookup(cf, "model.links");
			} else if (memcmp("path_loss", model_type_str,
				strlen("path_loss")) == 0) {
				/* calculate signal from positions */
				if (parse_path_loss(ctx, cf))
					goto fail;
			}
		}
	}

	if (per_file && error_probs) {
		w_flogf(ctx, LOG_ERR, stderr,
			"per_file and error_probs could not be used at the same time\n");
		goto fail;
	}

	ctx->get_link_snr = get_link_snr_from_snr_matrix;
	ctx->get_error_prob = _get_error_prob_from_snr;

	ctx->per_matrix = NULL;
	ctx->per_matrix_row_num = 0;
	if (per_file && read_per_file(ctx, per_file))
		goto fail;

	ctx->error_prob_matrix = NULL;
	if (error_probs) {
		ctx->error_prob_matrix = calloc(sizeof(double),
						count_ids * count_ids);
		if (!ctx->error_prob_matrix) {
			w_flogf(ctx, LOG_ERR, stderr,
				"Out of memory(error_prob_matrix)\n");
			goto fail;
		}

		ctx->get_link_snr = get_link_snr_default;
		ctx->get_error_prob = get_error_prob_from_matrix;

		default_prob = config_lookup(cf, "model.default_prob");
		if (default_prob) {
			default_prob_value = config_setting_get_float(
				default_prob);
			if (default_prob_value < 0.0 ||
			    default_prob_value > 1.0) {
				w_flogf(ctx, LOG_ERR, stderr,
					"model.default_prob should be in [0.0, 1.0]\n");
				goto fail;
			}
		}
	}

	link_map = calloc(sizeof(bool), count_ids * count_ids);
	if (!link_map) {
		w_flogf(ctx, LOG_ERR, stderr, "Out of memory\n");
		goto fail;
	}

	/* read snr values */
	for (i = 0; links && i < config_setting_length(links); i++) {
		config_setting_t *link;

		link = config_setting_get_elem(links, i);
		if (config_setting_length(link) != 3) {
			w_flogf(ctx, LOG_ERR, stderr, "Invalid link: expected (int,int,int)\n");
			goto fail;
		}
		start = config_setting_get_int_elem(link, 0);
		end = config_setting_get_int_elem(link, 1);
		snr = config_setting_get_int_elem(link, 2);

		if (start < 0 || start >= ctx->num_stas ||
		    end < 0 || end >= ctx->num_stas) {
			w_flogf(ctx, LOG_ERR, stderr, "Invalid link [%d,%d,%d]: index out of range\n",
					start, end, snr);
			goto fail;
		}
		ctx->snr_matrix[ctx->num_stas * start + end] = snr;
		link_map[ctx->num_stas * start + end] = true;
	}

	/* initialize with default_prob */
	for (start = 0; error_probs && start < ctx->num_stas; start++)
		for (end = start + 1; end < ctx->num_stas; end++) {
			ctx->error_prob_matrix[ctx->num_stas *
				start + end] =
			ctx->error_prob_matrix[ctx->num_stas *
				end + start] = default_prob_value;
		}

	/* read error probabilities */
	for (i = 0; error_probs &&
	     i < config_setting_length(error_probs); i++) {
		float error_prob_value;

		error_prob = config_setting_get_elem(error_probs, i);
		if (config_setting_length(error_prob) != 3) {
			w_flogf(ctx, LOG_ERR, stderr, "Invalid error probability: expected (int,int,float)\n");
			goto fail;
		}

		start = config_setting_get_int_elem(error_prob, 0);
		end = config_setting_get_int_elem(error_prob, 1);
		error_prob_value = config_setting_get_float_elem(error_prob, 2);

		if (start < 0 || start >= ctx->num_stas ||
		    end < 0 || end >= ctx->num_stas ||
		    error_prob_value < 0.0 || error_prob_value > 1.0) {
			w_flogf(ctx, LOG_ERR, stderr, "Invalid error probability [%d,%d,%f]\n",
				start, end, error_prob_value);
			goto fail;
		}

		ctx->error_prob_matrix[ctx->num_stas * start + end] =
			error_prob_value;
		link_map[ctx->num_stas * start + end] = true;
	}

	/*
	 * If any links are specified in only one direction, mirror them,
	 * making them symmetric.  If specified in both directions they
	 * can be asymmetric.
	 */
	for (i = 0; i < ctx->num_stas; i++) {
		for (j = 0; j < ctx->num_stas; j++) {
			if (i == j)
				continue;

			if (link_map[ctx->num_stas * i + j] &&
			    !link_map[ctx->num_stas * j + i]) {
				mirror_link(ctx, i, j);
			}
		}
	}

	free(link_map);
	config_destroy(cf);
	return 0;

fail:
	free(ctx->snr_matrix);
	free(ctx->error_prob_matrix);
	config_destroy(cf);
	return -EINVAL;
}

int clear_config(struct wmediumd *ctx) {
	free(ctx->sta_array);
	free(ctx->intf);
	free(ctx->snr_matrix);
	free(ctx->error_prob_matrix);
	free(ctx->config_path);

	ctx->sta_array = NULL;
	ctx->intf = NULL;
	ctx->snr_matrix = NULL;
	ctx->error_prob_matrix = NULL;
	ctx->config_path = NULL;

	while (!list_empty(&ctx->stations)) {
		struct station *station;

		station = list_first_entry(&ctx->stations,
					    struct station, list);

		list_del(&station->list);
		free(station);
	}

	return 0;
}
