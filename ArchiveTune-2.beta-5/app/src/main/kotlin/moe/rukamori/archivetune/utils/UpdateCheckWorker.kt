/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // FITUR UPDATE DIMATIKAN: 
        // Langsung kembalikan nilai sukses tanpa melakukan pengecekan apa pun
        // untuk mencegah pemborosan baterai dan error di latar belakang.
        return Result.success()
    }
}
