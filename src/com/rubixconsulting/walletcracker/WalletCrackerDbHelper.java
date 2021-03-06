package com.rubixconsulting.walletcracker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQuery;
import android.util.Log;

public class WalletCrackerDbHelper extends SQLiteOpenHelper {
  public static final String TABLE_PIN_CACHE = "pin_cache";
  public static final String DATABASE_NAME = "WalletCracker";
  public static final Integer PIN_ERROR = -1;
  private static final int DATABASE_VERSION = 5;
  protected static final String COLUMN_ID   = "id";
  protected static final String COLUMN_SALT = "salt";
  protected static final String COLUMN_HASH = "hash";
  protected static final String COLUMN_PIN  = "pin";
  protected static String createSql[] = {"CREATE TABLE "+TABLE_PIN_CACHE+" ("
                                         +  COLUMN_ID   + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                                         +  COLUMN_SALT + " INTEGER NOT NULL,"
                                         +  COLUMN_HASH + " TEXT NOT NULL CHECK (" + COLUMN_HASH + " <> ''),"
                                         +  COLUMN_PIN  + " INTEGER NOT NULL,"
                                         +  "UNIQUE (" + COLUMN_SALT + ", " + COLUMN_HASH + ", " + COLUMN_PIN + ")"
                                         + ")"};
  protected static String upgradeSql[][] = {
                                             {}, // version 1 to 2
                                             {}, // version 2 to 3
                                             {"DROP TABLE "+TABLE_PIN_CACHE, createSql[0]}, // version 3 to 4
                                             {"DROP TABLE "+TABLE_PIN_CACHE, createSql[0]} // version 4 to 5
                                           };
  protected final String TAG = this.getClass().getSimpleName();

  public WalletCrackerDbHelper(Context context) {
    super(context, DATABASE_NAME, null, DATABASE_VERSION);
  }

  private void execMultipleSQL(SQLiteDatabase db, String[] sql) {
    for (String s : sql) {
      if (s.trim().length() > 0) {
        Log.d(TAG, "sql: "+s);
        db.execSQL(s);
      }
    }
  }

  @Override public void onCreate(SQLiteDatabase db) {
    Log.i(TAG, "Creating database "+DATABASE_NAME);
    db.beginTransaction();
    try {
      execMultipleSQL(db, createSql);
      db.setTransactionSuccessful();
    } catch (SQLException e) {
      Log.e("Error creating database", e.toString());
    } finally {
      db.endTransaction();
      Log.i(TAG, "Successfully created database "+DATABASE_NAME);
    }
  }

  @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    Log.i(TAG, "Upgrading database "+DATABASE_NAME+" from version "+oldVersion+" to version "+newVersion);
    db.beginTransaction();
    try {
      final Integer max = Math.min(upgradeSql.length, newVersion - 1);
      for (Integer i = oldVersion-1; i < max; ++i) {
        execMultipleSQL(db, upgradeSql[i]);
      }
      db.setTransactionSuccessful();
    } catch (SQLException e) {
      Log.e("Error upgrading database: ", e.toString());
    } finally {
      db.endTransaction();
      Log.i(TAG, "Successfully upgraded database "+DATABASE_NAME+" to version "+newVersion);
    }
  }

  public static class PinCacheCursor extends SQLiteCursor {
    private static final String QUERY = "SELECT pin FROM " + TABLE_PIN_CACHE;

    public PinCacheCursor(SQLiteDatabase db, SQLiteCursorDriver driver, String editTable, SQLiteQuery query) {
      super(db, driver, editTable, query);
    }

    private static class Factory implements SQLiteDatabase.CursorFactory {
      @Override public Cursor newCursor(SQLiteDatabase db, SQLiteCursorDriver driver, String editTable, SQLiteQuery query) {
        return new PinCacheCursor(db, driver, editTable, query);
      }
    }

    public Integer getColPin() {
      return getInt(getColumnIndexOrThrow("pin"));
    }
  }

  public Integer getPin(Long salt, String hash) {
    PinCacheCursor c = null;
    try {
      final String sql = PinCacheCursor.QUERY + " WHERE salt = ? AND hash = ?";
      final String args[] = {salt.toString(), hash};
      SQLiteDatabase d = getReadableDatabase();
      c = (PinCacheCursor) d.rawQueryWithFactory(new PinCacheCursor.Factory(), sql, args, null);
      if (c.moveToFirst() == false) {
        return null;
      }
      return c.getColPin();
    } finally {
      if (c != null) {
        c.close();
      }
    }
  }

  public void addPin(Long salt, String hash, Integer pin) {
    Integer existing = getPin(salt, hash);
    Log.i(TAG, "existing pin is: "+existing);
    if (existing == pin) {
      Log.i(TAG, "not adding pin, it already exists in cache");
      return;
    } else if (existing != null){
      Log.e(TAG, "pin is already cached, but with a different value");
      return;
    }
    Log.i(TAG, "inserting into pin cache salt: "+salt+" hash: "+hash+" pin: "+pin);
    ContentValues values = new ContentValues();
    values.put("salt", salt);
    values.put("hash", hash);
    values.put("pin", pin);
    try{
      getWritableDatabase().insert(TABLE_PIN_CACHE, null, values);
    } catch (SQLException e) {
      Log.e(TAG, "Error adding pin: "+e.toString());
    }
  }
}