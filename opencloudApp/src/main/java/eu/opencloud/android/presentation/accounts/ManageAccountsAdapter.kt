/**
 * openCloud Android client application
 *
 * @author Javier Rodríguez Pérez
 * @author Aitor Ballesteros Pavón
 * @author Jorge Aguado Recio
 *
 * Copyright (C) 2024 ownCloud GmbH.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package eu.opencloud.android.presentation.accounts

import android.accounts.Account
import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import eu.opencloud.android.R
import eu.opencloud.android.databinding.AccountActionBinding
import eu.opencloud.android.databinding.AccountItemBinding
import eu.opencloud.android.domain.user.model.UserQuotaState
import eu.opencloud.android.domain.user.model.UserQuota
import eu.opencloud.android.extensions.setAccessibilityRole
import eu.opencloud.android.lib.common.OpenCloudAccount
import eu.opencloud.android.presentation.authentication.AccountUtils
import eu.opencloud.android.presentation.avatar.AvatarUtils
import eu.opencloud.android.utils.DisplayUtils
import eu.opencloud.android.utils.PreferenceUtils
import timber.log.Timber
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ManageAccountsAdapter(
    private val accountListener: AccountAdapterListener,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var accountItemsList = listOf<AccountRecyclerItem>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return if (viewType == AccountManagementRecyclerItemViewType.ITEM_VIEW_ACCOUNT.ordinal) {
            val view = inflater.inflate(R.layout.account_item, parent, false)
            view.filterTouchesWhenObscured = PreferenceUtils.shouldDisallowTouchesWithOtherVisibleWindows(parent.context)
            view.setAccessibilityRole(className = Button::class.java)
            AccountManagementViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.account_action, parent, false)
            view.filterTouchesWhenObscured = PreferenceUtils.shouldDisallowTouchesWithOtherVisibleWindows(parent.context)
            NewAccountViewHolder(view)
        }

    }

    fun submitAccountList(accountList: List<AccountRecyclerItem>) {
        accountItemsList = accountList
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is AccountManagementViewHolder -> {
                val accountItem = getItem(position) as AccountRecyclerItem.AccountItem
                val account: Account = accountItem.account
                val accountAvatarRadiusDimension = holder.itemView.context.resources.getDimension(R.dimen.list_item_avatar_icon_radius)

                try {
                    val oca = OpenCloudAccount(account, holder.itemView.context)
                    holder.binding.name.text = oca.displayName
                } catch (e: Exception) {
                    Timber.w(
                        e, "Account not found right after being read :\\ ; using account name instead of display name"
                    )
                    holder.binding.name.text = AccountUtils.getUsernameOfAccount(account.name)
                }
                holder.binding.name.tag = account.name

                holder.binding.account.text = DisplayUtils.convertIdn(account.name, false)

                updateQuota(
                    quotaText = holder.binding.manageAccountsQuotaText,
                    quotaBar = holder.binding.manageAccountsQuotaBar,
                    userQuota = accountItem.userQuota,
                    context = holder.itemView.context
                )


                try {
                    val avatarUtils = AvatarUtils()
                    holder.itemView.findViewTreeLifecycleOwner()?.lifecycleScope?.launch(Dispatchers.IO) {
                        val loader = eu.opencloud.android.presentation.thumbnails.ThumbnailsRequester.getCoilImageLoader(account)
                        withContext(Dispatchers.Main) {
                            avatarUtils.loadAvatarForAccount(
                                holder.binding.icon,
                                account,
                                accountAvatarRadiusDimension,
                                loader
                            )
                        }
                    }
                } catch (e: java.lang.Exception) {
                    Timber.e(e, "Error calculating RGB value for account list item.")
                    // use user icon as a fallback
                    holder.binding.icon.setImageResource(R.drawable.ic_user)
                }

                if (AccountUtils.getCurrentOpenCloudAccount(holder.itemView.context).name == account.name) {
                    holder.binding.ticker.visibility = View.VISIBLE
                } else {
                    holder.binding.ticker.visibility = View.INVISIBLE
                }

                /// bind listener to clean local storage from account
                holder.binding.cleanAccountLocalStorageButton.apply {
                    setImageResource(R.drawable.ic_clean_account)
                    setOnClickListener { accountListener.cleanAccountLocalStorage(account) }
                }
                /// bind listener to remove account
                holder.binding.removeButton.apply {
                    setImageResource(R.drawable.ic_action_delete_grey)
                    setOnClickListener { accountListener.removeAccount(account) }
                }

                ///bind listener to switchAccount
                holder.itemView.apply {
                    setOnClickListener { accountListener.switchAccount(position) }
                }
            }
            is NewAccountViewHolder -> {
                holder.binding.icon.setImageResource(R.drawable.ic_account_plus)
                holder.binding.name.setText(R.string.prefs_add_account)
                holder.binding.name.setAccessibilityRole(className = Button::class.java)

                // bind action listener
                holder.binding.constraintLayoutAction.setOnClickListener {
                    accountListener.createAccount()
                }
            }
        }

    }

    override fun getItemCount(): Int = accountItemsList.size

    fun getItem(position: Int) = accountItemsList[position]

    private fun updateQuota(quotaText: TextView, quotaBar: ProgressBar, userQuota: UserQuota, context: Context) {
        when {
            userQuota.available == -4L -> { // Light users
                quotaBar.visibility = View.GONE
                quotaText.text = context.getString(R.string.drawer_unavailable_used_storage)
            }

            userQuota.available < 0 -> { // Pending, unknown or unlimited free storage. The progress bar is hid
                quotaBar.visibility = View.GONE
                quotaText.text = DisplayUtils.bytesToHumanReadable(userQuota.used, context, false)
            }

            userQuota.available == 0L -> { // Exceeded storage. Value over 100%
                quotaBar.apply {
                    progress = 100
                    progressTintList = ColorStateList.valueOf(resources.getColor(R.color.quota_exceeded))
                }
                if (userQuota.state == UserQuotaState.EXCEEDED) {
                    quotaText.text = String.format(
                        context.getString(R.string.manage_accounts_quota),
                        DisplayUtils.bytesToHumanReadable(userQuota.used, context, false),
                        DisplayUtils.bytesToHumanReadable(userQuota.getTotal(), context, false)
                    )
                } else { // oC10
                    quotaText.text = context.getString(R.string.drawer_exceeded_quota)
                }
            }

            else -> { // Limited storage. Value under 100%
                if (userQuota.state == UserQuotaState.CRITICAL || userQuota.state == UserQuotaState.EXCEEDED ||
                    userQuota.state == UserQuotaState.NEARING) { // Value over 75%
                    quotaBar.apply {
                        progressTintList = ColorStateList.valueOf(resources.getColor(R.color.quota_exceeded))
                    }
                }
                quotaBar.progress = userQuota.getRelative().toInt()
                quotaText.text = String.format(
                    context.getString(R.string.manage_accounts_quota),
                    DisplayUtils.bytesToHumanReadable(userQuota.used, context, false),
                    DisplayUtils.bytesToHumanReadable(userQuota.getTotal(), context, false)
                )
            }
        }
    }

    sealed class AccountRecyclerItem {
        data class AccountItem(val account: Account, val userQuota: UserQuota) : AccountRecyclerItem()
        object NewAccount : AccountRecyclerItem()
    }

    class AccountManagementViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val binding = AccountItemBinding.bind(itemView)
    }

    class NewAccountViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val binding = AccountActionBinding.bind(itemView)
    }

    override fun getItemViewType(position: Int): Int =
        when (getItem(position)) {
            is AccountRecyclerItem.AccountItem -> AccountManagementRecyclerItemViewType.ITEM_VIEW_ACCOUNT.ordinal
            is AccountRecyclerItem.NewAccount -> AccountManagementRecyclerItemViewType.ITEM_VIEW_ADD.ordinal
        }

    enum class AccountManagementRecyclerItemViewType {
        ITEM_VIEW_ACCOUNT, ITEM_VIEW_ADD
    }

    /**
     * Listener interface for Activities using the [ManageAccountsAdapter]
     */
    interface AccountAdapterListener {
        fun removeAccount(account: Account)
        fun cleanAccountLocalStorage(account: Account)
        fun createAccount()
        fun switchAccount(position: Int)
    }

}
