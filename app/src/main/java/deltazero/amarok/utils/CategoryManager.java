package deltazero.amarok.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import deltazero.amarok.PrefMgr;

public class CategoryManager {
    public static final String CAT_SOCIAL = "social";
    public static final String CAT_GAMES = "games";
    public static final String CAT_PORN = "porn";
    public static final String CAT_OTHERS = "outros";

    // Default sites for each category
    private static final List<String> DEF_SOCIAL = Arrays.asList(
            "instagram.com", "facebook.com", "tiktok.com", "twitter.com",
            "x.com", "snapchat.com", "youtube.com", "reddit.com",
            "whatsapp.com", "telegram.org", "discord.com"
    );
    private static final List<String> DEF_GAMES = Arrays.asList(
            "roblox.com", "miniclip.com", "friv.com", "poki.com",
            "crazygames.com", "gameflare.com", "y8.com", "kizi.com",
            "agame.com", "armor games.com", "kongregate.com", "steam.com"
    );
    private static final List<String> DEF_PORN = Arrays.asList(
            "xvideos.com", "pornhub.com", "xnxx.com", "redtube.com",
            "youporn.com", "spankbang.com", "eporner.com", "xhamster.com",
            "chaturbate.com", "brazzers.com", "onlyfans.com", "xncam.com", "chaturbate.com"
    );

    public static class FirewallCategory {
        public String id;
        public String name;
        public String icon;
        public boolean isEnabled;
        public List<String> domains;

        public FirewallCategory(String id, String name, String icon) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.isEnabled = false;
            this.domains = new ArrayList<>();
        }
    }

    public static Map<String, FirewallCategory> getCategories() {
        Map<String, FirewallCategory> categories = new HashMap<>();
        categories.put(CAT_SOCIAL, new FirewallCategory(CAT_SOCIAL, "Redes Sociais", "📱"));
        categories.put(CAT_GAMES, new FirewallCategory(CAT_GAMES, "Jogos Online", "🎮"));
        categories.put(CAT_PORN, new FirewallCategory(CAT_PORN, "Pornografia", "🔞"));
        categories.put(CAT_OTHERS, new FirewallCategory(CAT_OTHERS, "Outros", "🌐"));

        Set<String> rawUrls = PrefMgr.getBlockedUrls();
        boolean needsSave = false;

        // Populate with rawUrls
        for (String item : rawUrls) {
            if (item.startsWith("1:") || item.startsWith("0:")) {
                boolean enabled = item.startsWith("1:");
                int secondColon = item.indexOf(':', 2);
                if (secondColon != -1) {
                    String catId = item.substring(2, secondColon);
                    String domain = item.substring(secondColon + 1);
                    if (categories.containsKey(catId)) {
                        FirewallCategory cat = categories.get(catId);
                        cat.domains.add(domain);
                        if (enabled) {
                            cat.isEnabled = true; // If ANY site is 1, the category is visually ON
                        }
                    } else {
                        // Fallback if category ID is unknown
                        categories.get(CAT_OTHERS).domains.add(domain);
                        if (enabled) categories.get(CAT_OTHERS).isEnabled = true;
                        needsSave = true;
                    }
                }
            } else {
                // Legacy URL format
                categories.get(CAT_OTHERS).domains.add(item);
                categories.get(CAT_OTHERS).isEnabled = true;
                needsSave = true;
            }
        }

        // Add defaults if missing completely (like on fresh install or migration)
        if (addDefaultsIfMissing(categories.get(CAT_SOCIAL), DEF_SOCIAL)) needsSave = true;
        if (addDefaultsIfMissing(categories.get(CAT_GAMES), DEF_GAMES)) needsSave = true;
        if (addDefaultsIfMissing(categories.get(CAT_PORN), DEF_PORN)) needsSave = true;

        if (needsSave) {
            saveCategories(categories.values());
        }

        return categories;
    }

    private static boolean addDefaultsIfMissing(FirewallCategory cat, List<String> defaults) {
        boolean changed = false;
        if (cat.domains.isEmpty()) {
            cat.domains.addAll(defaults);
            changed = true;
            cat.isEnabled = false; // Default off, let user turn it on
        }
        return changed;
    }

    public static void saveCategories(Iterable<FirewallCategory> categories) {
        Set<String> newRawUrls = new HashSet<>();
        for (FirewallCategory cat : categories) {
            String prefix = cat.isEnabled ? "1:" : "0:";
            for (String domain : cat.domains) {
                newRawUrls.add(prefix + cat.id + ":" + domain);
            }
        }
        PrefMgr.setBlockedUrls(newRawUrls);
    }
}
