/*
 * Copyright (c) Techbee e.U.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.techbee.jtx.flavored

import android.app.Activity
import androidx.lifecycle.LiveData

interface BillingManagerDefinition {

    var isAdFreePurchased: LiveData<Boolean>

    val adFreePrice: LiveData<String?>
    val adFreePurchaseDate: LiveData<String?>
    val adFreeOrderId: LiveData<String?>

    /**
     * Initialises the billing client (if not initialised yet)
     * and makes the variable billingClient available for further use.
     * The initialisiation also calls querySkuDetails().
     */
    fun initialise(activity: Activity)

    /**
     * This function launches the billing flow from Google Play.
     * It shows a bar on the bototm of the page where the user can buy the item.
     * The passed skuDetails are currently [BillingManager.adfreeSkuDetails].
     */
    fun launchBillingFlow(activity: Activity)

}