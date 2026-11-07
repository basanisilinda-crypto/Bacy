package za.co.bacy.ui;

import android.content.Context;

/** Shared product illustration; account and device readings remain server supplied. */
public final class PhoneArtwork {
    private PhoneArtwork() {}

    public static int resource(Context context) {
        return context.getResources().getIdentifier("loop_phone_pair", "drawable", context.getPackageName());
    }
}
