/** ----------------------------------------------------------------------
 *
 * Copyright (C) 2022 ST Microelectronics S.A.
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
 *
 *
 ----------------------------------------------------------------------*/
#ifndef __ST21NFC_DEV__
#define __ST21NFC_DEV__

typedef struct {
  nfc_stack_callback_t* p_cback;
  nfc_stack_data_callback_t* p_data_cback;
  HALHANDLE hHAL;
  nfc_stack_callback_t* p_cback_unwrap;
} st21nfc_dev_t;

#endif
