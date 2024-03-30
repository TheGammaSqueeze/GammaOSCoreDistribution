/******************************************************************************
 *
 *  Copyright 2018-2019 NXP
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

/**
 * \addtogroup eSe_PAL_Spi
 * \brief PAL SPI port implementation for linux
 * @{ */
#ifndef _PHNXPESE_PAL_SPI_H
#define _PHNXPESE_PAL_SPI_H

/* Basic type definitions */
#include <IntervalTimer.h>
#include <StateMachineInfo.h>
#include <phNxpEsePal.h>

/*!
 * \brief Start of frame marker
 */
#define SEND_PACKET_SOF 0x5A
/*!
 * \brief ESE Poll timeout (max 2 seconds)
 */
#define ESE_POLL_TIMEOUT (2 * 1000)
/*!
 * \brief ESE Poll timeout (max 2 seconds)
 */
#define ESE_NAD_POLLING_MAX (2 * 1000)

/*!
 * \brief ESE wakeup delay in case of write error retry
 */
#define WRITE_WAKE_UP_DELAY 1000

/*!
 * \brief ESE SOF polling delay
 */
#define READ_WAKE_UP_DELAY 100

/*!
 * \brief ESE wakeup delay in case of write error retry
 */
#define NAD_POLLING_SCALER 10

/*!
 * \brief Magic type specific to the ESE device driver
 */
#define P61_MAGIC 0xEA

/* Function declarations */
/**
 * \ingroup eSe_PAL_Spi
 * \brief This function is used to close the ESE device
 *
 * \retval None
 *
 */

void phPalEse_spi_close(void* pDevHandle);

/**
 * \ingroup eSe_PAL_Spi
 * \brief Open and configure ESE device
 *
 * \param[in]       pphPalEse_Config_t: Config to open the device
 *
 * \retval  ESESTATUS On Success ESESTATUS_SUCCESS else proper error code
 *
 */
ESESTATUS phPalEse_spi_open_and_configure(pphPalEse_Config_t pConfig);

/**
 * \ingroup eSe_PAL_Spi
 * \brief Reads requested number of bytes from ESE into given buffer
 *
 * \param[in]    pDevHandle       - valid device handle
 **\param[in]    pBuffer          - buffer for read data
 **\param[in]    nNbBytesToRead   - number of bytes requested to be read
 *
 * \retval   numRead      - number of successfully read bytes.
 * \retval      -1             - read operation failure
 *
 */
int phPalEse_spi_read(void* pDevHandle, uint8_t* pBuffer, int nNbBytesToRead);

/**
 * \ingroup eSe_PAL_Spi
 * \brief Writes requested number of bytes from given buffer into pn547 device
 *
 * \param[in]    pDevHandle               - valid device handle
 * \param[in]    pBuffer                     - buffer to write
 * \param[in]    nNbBytesToWrite       - number of bytes to write
 *
 * \retval  numWrote   - number of successfully written bytes
 * \retval      -1         - write operation failure
 *
 */
int phPalEse_spi_write(void* pDevHandle, uint8_t* pBuffer, int nNbBytesToWrite);

/**
 * \ingroup eSe_PAL_Spi
 * \brief Exposed ioctl by ESE driver
 *
 * \param[in]    eControlCode       - phPalEse_ControlCode_t for the respective
 *configs
 * \param[in]    pDevHandle           - valid device handle
 * \param[in]    pBuffer              - buffer for read data
 * \param[in]    level                  - reset level
 *
 * \retval    0   - ioctl operation success
 * \retval   -1  - ioctl operation failure
 *
 */
ESESTATUS phPalEse_spi_ioctl(phPalEse_ControlCode_t eControlCode,
                             void* pDevHandle, long level);

/**
 * \ingroup eSe_PAL_Spi
 * \brief Print packet data
 *
 * \param[in]    pString           - String to be printed
 * \param[in]    p_data               - data to be printed
 * \param[in]    len                  - Length of data to be printed
 *
 * \retval   void
 *
 */
void phPalEse_spi_print_packet(const char* pString, const uint8_t* p_data,
                               uint16_t len);
/**
 * \ingroup eSe_PAL_Spi
 * \brief This function  suspends execution of the calling thread for
 *                  (at least) usec microseconds
 *
 * \param[in]    usec           - number of micro seconds to sleep
 *
 * \retval   void
 *
 */
void phPalEse_spi_sleep(uint32_t usec);

/**
 * \ingroup eSe_PAL_Spi
 * \brief This function stops a rf debounce timer
 *
 * \param[in]
 *
 * \retval   void
 *
 */
void phPalEse_spi_stop_debounce_timer();

/**
 * \ingroup eSe_PAL_Spi
 * \brief This function starts a rf denounce timer
 *
 * \param[in]    millisecs    - number of millisec
 *
 * \retval   void
 *
 */
void phPalEse_spi_start_debounce_timer(unsigned long millisecs);

/**
 * \ingroup eSe_PAL_Spi
 * \brief This function sends rf off event to state machine
 *
 * \param[in]
 *
 * \retval   void
 *
 */
void phPalEse_spi_rf_off_timer_expired_cb(union sigval);

/*******************************************************************************
**
** Function         phPalEse_spi_match_app_signatures
**
** Description
**
** Parameters       none
**
** Returns          true/false
**
*******************************************************************************/
ESESTATUS phPalEse_spi_match_app_signatures(std::vector<uint8_t> signature);

/** @} */
#endif /*  _PHNXPESE_PAL_SPI_H    */
