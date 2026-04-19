package app.pwhs.dunebox.stub

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * Base stub ContentProvider. Routes content queries from virtual apps
 * through the DuneBox engine to the correct virtual data store.
 */
open class StubContentProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                       selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?,
                        selectionArgs: Array<out String>?): Int = 0

    // Process :p0
    class P0 {
        class S0 : StubContentProvider()
        class S1 : StubContentProvider()
    }

    // Process :p1
    class P1 {
        class S0 : StubContentProvider()
    }
}
