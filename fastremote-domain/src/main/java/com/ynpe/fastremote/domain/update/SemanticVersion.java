// SPDX-License-Identifier: MPL-2.0
package com.ynpe.fastremote.domain.update;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

/** Strict SemVer value used to compare the installed application with a published release. */
public record SemanticVersion(int major, int minor, int patch, List<String> prerelease)
        implements Comparable<SemanticVersion> {
    private static final java.util.regex.Pattern PATTERN = java.util.regex.Pattern.compile(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
                    + "(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$");

    public SemanticVersion {
        if (major < 0 || minor < 0 || patch < 0) throw new IllegalArgumentException("negative version");
        prerelease = List.copyOf(Objects.requireNonNull(prerelease, "prerelease"));
    }

    public static SemanticVersion parse(String value) {
        String text = Objects.requireNonNull(value, "value").strip();
        var matcher = PATTERN.matcher(text);
        if (!matcher.matches()) throw new IllegalArgumentException("Invalid semantic version: " + text);
        List<String> identifiers = matcher.group(4) == null
                ? List.of() : List.of(matcher.group(4).split("\\."));
        for (String identifier : identifiers) {
            if (identifier.chars().allMatch(Character::isDigit)
                    && identifier.length() > 1 && identifier.charAt(0) == '0') {
                throw new IllegalArgumentException("Invalid semantic version: " + text);
            }
        }
        return new SemanticVersion(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)), identifiers);
    }

    @Override
    public int compareTo(SemanticVersion other) {
        int result = Integer.compare(major, other.major);
        if (result == 0) result = Integer.compare(minor, other.minor);
        if (result == 0) result = Integer.compare(patch, other.patch);
        if (result != 0) return result;
        if (prerelease.isEmpty()) return other.prerelease.isEmpty() ? 0 : 1;
        if (other.prerelease.isEmpty()) return -1;
        int length = Math.min(prerelease.size(), other.prerelease.size());
        for (int index = 0; index < length; index++) {
            result = compareIdentifier(prerelease.get(index), other.prerelease.get(index));
            if (result != 0) return result;
        }
        return Integer.compare(prerelease.size(), other.prerelease.size());
    }

    private static int compareIdentifier(String left, String right) {
        boolean leftNumeric = left.chars().allMatch(Character::isDigit);
        boolean rightNumeric = right.chars().allMatch(Character::isDigit);
        if (leftNumeric && rightNumeric) return new BigInteger(left).compareTo(new BigInteger(right));
        if (leftNumeric != rightNumeric) return leftNumeric ? -1 : 1;
        return left.compareTo(right);
    }
}
