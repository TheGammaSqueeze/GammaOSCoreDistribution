/*
 * Copyright (C) 2022 Arm Limited.
 * SPDX-License-Identifier: Apache-2.0
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

package arm.graphics;

/**
 * Used to extend the built-in chroma-siting types
 */
@VintfStability
@Backing(type="long")
/* Temporary values intended to not clash with any new official values that may be added */
enum ChromaSiting {
    /**
     * Vertical cosited, Horizontally interstitially
     */
    COSITED_VERTICAL = 1 << 8,
    /**
     * Both vertically and horizontally cosited
     */
    COSITED_BOTH = 2 << 8,
}
