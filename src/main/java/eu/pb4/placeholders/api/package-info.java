/**
 * Compile-time stand-ins for the parts of Text Placeholder API (Patbox's placeholder-api, the mod TAB and others read
 * placeholders through on Fabric) that Extreme Dimensions uses, with the same names and signatures as its 26.3 build.
 * They are left out of the mod's jar: with placeholder-api installed its real classes are the ones loaded, and without
 * it nothing here is ever touched (see com.extremedimensions.compat.PlaceholderApiHook).
 */
package eu.pb4.placeholders.api;
