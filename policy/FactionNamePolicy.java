package me.krunsh.kfaction.policy;

import java.util.Collection;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Validation déterministe des noms de faction, partagée par create/rename. */
public final class FactionNamePolicy {

    private FactionNamePolicy() {
    }

    public static boolean isValid(String name, int minLength, int maxLength,
                                  String regex, Collection<String> blockedWords) {
        if (name == null || name.length() < minLength || name.length() > maxLength) {
            return false;
        }
        try {
            if (!Pattern.matches(regex, name)) {
                return false;
            }
        } catch (PatternSyntaxException exception) {
            return false;
        }
        if (blockedWords != null) {
            for (String blocked : blockedWords) {
                if (blocked != null && name.equalsIgnoreCase(blocked.trim())) {
                    return false;
                }
            }
        }
        return true;
    }
}
