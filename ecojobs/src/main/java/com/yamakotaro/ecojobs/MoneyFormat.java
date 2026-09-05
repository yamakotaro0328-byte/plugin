package com.yamakotaro.ecojobs;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Formats a money amount consistently across EcoJobs - a thousands separator plus 2 decimal
 * places (e.g. "1,234.50") - instead of the bare {@code String.format("%.2f", ...)} scattered
 * across the plugin at every place a real currency amount is shown.
 *
 * <p>This intentionally does not reuse EcoTP's {@code ChatUtil.formatMoney}/
 * {@code Messages#formatMoney}: those round to a whole number and append a currency unit word
 * (e.g. "5 coins"), which would hide the sub-1 amounts many EcoJobs actions pay (digger's
 * 0.2/block, builder's 0.15/block would both round to "0"). EcoJobs has no compile-time
 * dependency on EcoTP (see {@link EcoJobsPlugin}'s class doc) and this keeps it that way - it's a
 * small local helper, not a shared one.
 *
 * <p>Only for genuine currency amounts - not xp, and not multipliers (a booster's "2.00x" or a
 * job-override's payout multiplier isn't money, so those keep their own plain formatting).
 */
public final class MoneyFormat {

    private MoneyFormat() {
    }

    /** A fresh DecimalFormat per call - DecimalFormat isn't thread-safe, and this is cheap. */
    public static String format(double amount) {
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US)).format(amount);
    }
}
