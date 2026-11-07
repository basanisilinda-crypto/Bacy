package za.co.bacy.ui;

import android.content.Context;
import android.graphics.Typeface;

/** Bundled, offline typography shared by the customer, owner and preview applications. */
public final class BrandFonts {
    private static Typeface regular, medium, heading;
    private BrandFonts() {}

    public static synchronized Typeface sans(Context context, boolean strong) {
        if (strong) {
            if (medium == null) medium = load(context, "dm_sans_medium", "sans-serif", Typeface.NORMAL);
            return medium;
        }
        if (regular == null) regular = load(context, "dm_sans_regular", "sans-serif", Typeface.NORMAL);
        return regular;
    }

    public static synchronized Typeface heading(Context context) {
        if (heading == null) heading = load(context, "fraunces_regular", "serif", Typeface.NORMAL);
        return heading;
    }

    private static Typeface load(Context context, String name, String fallback, int style) {
        int id = context.getResources().getIdentifier(name, "font", context.getPackageName());
        if (id != 0) {
            try { return context.getResources().getFont(id); }
            catch (android.content.res.Resources.NotFoundException ignored) { }
        }
        return Typeface.create(fallback, style);
    }
}
