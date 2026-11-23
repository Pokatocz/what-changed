import com.pokato.whatchanged.core.VersionCompare;

/**
 * Unit tests for VersionCompare (no Minecraft needed). Run with tools/test-versions.sh.
 * Each case: old version, new version, expected "is this a downgrade?".
 */
public class VersionCompareTest {
    static int fails = 0, runs = 0;

    static void down(String old, String now, boolean expected) {
        check("isDowngrade", old, now, VersionCompare.isDowngrade(old, now), expected);
    }

    static void mod(String old, String now, boolean expected, String... mc) {
        check("isModDowngrade", old, now, VersionCompare.isModDowngrade(old, now, mc), expected);
    }

    static void check(String what, String old, String now, boolean got, boolean expected) {
        runs++;
        if (got != expected) {
            fails++;
            System.out.println("FAIL " + what + "(" + old + " -> " + now + ") = " + got + ", expected " + expected);
        }
    }

    public static void main(String[] args) {
        // --- unchanged behaviour ---------------------------------------------------------------
        down("1.2.0", "1.10.0", false);
        down("1.10.0", "1.9.0", true);
        down("1.2", "1.2.0", false);
        down("1.2.0", "1.2", false);
        down("1.2.0", "1.2.1", false);
        down("1.2.1", "1.2.0", true);
        down("0.92.0+1.20.1", "0.92.1+1.20.1", false);
        down("0.92.1+1.20.1", "0.92.0+1.21", true);
        down("v2", "v3", false);
        down("v3", "v2", true);
        down("mc1.21-5.4.2", "mc1.21-5.4.3", false);
        down("mc1.21-5.4.3", "mc1.21-5.4.2", true);
        down("abc123", "def456", false);
        down("def456", "abc123", false);
        down("1.2.0-beta.3", "1.2.0", false);          // pre-release -> release
        down("1.2.0", "1.2.0-beta.3", true);           // release -> pre-release
        down("1.2.0-beta.2", "1.2.0-beta.3", false);
        down("1.2.0-beta.3", "1.2.0-beta.2", true);
        down("1.2.0-alpha", "1.2.0-beta", false);
        down("1.2.0-beta", "1.2.0-alpha", true);
        down("1.2.0-pre1", "1.2.0-rc1", false);
        down("1.2.0-rc1", "1.2.0-pre1", true);
        down("1.2.0-rc.2", "1.2.0", false);
        down("1.2.3-SNAPSHOT", "1.2.3", false);
        down("1.2.3", "1.2.3-SNAPSHOT", true);
        down("1.20.1-forge-2.3.0", "1.20.1-forge-2.4.0", false);
        down("1.20.1-forge-2.4.0", "1.20.1-forge-2.3.0", true);
        down("1.21.4-pre1", "1.21.4", false);          // Minecraft itself
        down("1.21.4", "1.21.4-pre1", true);
        down("24w14a", "24w15a", false);
        down("24w15a", "24w14a", true);
        down("21.1.77-beta", "21.1.80", false);        // loader versions
        down("52.1.0", "52.0.16", true);
        mod("1.21.1-0.0.5", "1.21.1-1.3.0", false, "1.21.1", "1.21.1");
        mod("1.21.1-0.0.5", "1.3.0", false, "1.21.1", "1.21.1");
        mod("1.3.2", "1.3.4", false, "1.21.1", "1.21.1");
        mod("15.9.0", "15.8.2", true, "1.21.1", "1.21.1");
        mod("forge-1.20.1-2.0", "forge-1.20.1-1.9", true, "1.20.1", "1.20.1");
        mod("2.0.0-1.20.1", "1.9.0-1.21.1", false, "1.20.1", "1.21.1");   // Minecraft line went up: an update
        mod("1.9.0-1.21.1", "2.0.0-1.20.1", true, "1.21.1", "1.20.1");

        // --- the fix: suffixes that are not a pre-release ---------------------------------------
        down("1.2.3", "1.2.3-hotfix", false);          // the CurseForge comment
        down("1.2.3", "1.2.3-Hotfix", false);
        down("1.2.3", "1.2.3-HOTFIX-2", false);
        down("1.2.3", "1.2.3-hotfix2", false);
        down("1.2.3-hotfix", "1.2.3-hotfix2", false);
        down("1.2.3-hotfix2", "1.2.3-hotfix", true);
        down("1.2.3-hotfix", "1.2.3", true);
        down("1.2.3-hotfix", "1.2.4", false);
        down("1.2.3", "1.2.3-fix", false);
        down("1.2.3", "1.2.3-bugfix", false);
        down("1.2.3", "1.2.3-patch1", false);
        down("1.2.3", "1.2.3-hf", false);
        down("1.2.3", "1.2.3-r2", false);
        down("1.2.3", "1.2.3-build.5", false);
        down("1.2.3-build.5", "1.2.3-build.6", false);
        down("1.2.3-beta", "1.2.3-hotfix", false);
        down("1.2.3-hotfix", "1.2.3-beta", true);
        down("1.2.3-hotfix", "1.2.3-patch", false);    // two different fix words: can't tell
        // labels that say nothing about the order
        down("1.2.3", "1.2.3-fabric", false);
        down("1.2.3-fabric", "1.2.3", false);
        down("1.2.3-forge", "1.2.3-neoforge", false);
        down("1.2.3-neoforge", "1.2.3-forge", false);
        down("1.2.3-release", "1.2.3", false);
        down("1.2.3", "1.2.3-final", false);
        down("1.2.3-beta-fabric", "1.2.3-fabric", false);
        // unknown words: can't tell, never a downgrade
        down("1.2.3", "1.2.3-ultimate", false);
        down("1.2.3-ultimate", "1.2.3", false);
        down("1.2.3-legacy", "1.2.3-modern", false);
        down("1.2.3", "1.2.3a", false);
        down("1.2.3a", "1.2.3b", false);
        down("1.2.3b", "1.2.3a", false);
        // pre-release words glued to the number
        down("1.0.0.beta.3", "1.0.0", false);
        down("1.0.0beta3", "1.0.0", false);
        down("1.0.0", "1.0.0beta3", true);
        down("1.0b2", "1.0", false);
        down("1.0", "1.0b2", true);
        down("1.0a1", "1.0b1", false);
        // the same inside a mod version with the Minecraft version in it
        mod("2.1.0-1.21.1", "2.1.0-hotfix-1.21.1", false, "1.21.1", "1.21.1");
        mod("1.21.1-2.1.0", "1.21.1-2.1.0-hotfix", false, "1.21.1", "1.21.1");
        mod("mc1.21.1-2.1.0", "mc1.21.1-2.1.0-Hotfix", false, "1.21.1", "1.21.1");
        mod("2.1.0-hotfix-1.21.1", "2.1.0-1.21.1", false, "1.21.1", "1.21.1");   // the full strings disagree: not sure
        mod("1.21.1-2.1.0-hotfix", "1.21.1-2.1.0", true, "1.21.1", "1.21.1");
        mod("2.1.0", "2.1.0-beta.1", true, "1.21.1", "1.21.1");

        System.out.println(runs + " cases, " + fails + " failed");
        System.exit(fails == 0 ? 0 : 1);
    }
}
