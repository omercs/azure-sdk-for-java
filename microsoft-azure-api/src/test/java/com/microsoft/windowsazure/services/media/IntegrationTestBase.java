package com.microsoft.windowsazure.services.media;

import static org.junit.Assert.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.ExpectedException;

import com.microsoft.windowsazure.services.core.Configuration;
import com.microsoft.windowsazure.services.media.models.AccessPolicy;
import com.microsoft.windowsazure.services.media.models.AccessPolicyInfo;
import com.microsoft.windowsazure.services.media.models.Asset;
import com.microsoft.windowsazure.services.media.models.AssetInfo;
import com.microsoft.windowsazure.services.media.models.ContentKey;
import com.microsoft.windowsazure.services.media.models.ContentKeyInfo;
import com.microsoft.windowsazure.services.media.models.ListResult;
import com.microsoft.windowsazure.services.media.models.Locator;
import com.microsoft.windowsazure.services.media.models.LocatorInfo;

public abstract class IntegrationTestBase {
    protected static MediaContract service;
    protected static Configuration config;

    protected static final String testAssetPrefix = "testAsset";
    protected static final String testPolicyPrefix = "testPolicy";
    protected static final String testContentKeyPrefix = "testContentKey";

    protected static final String validButNonexistAssetId = "nb:cid:UUID:0239f11f-2d36-4e5f-aa35-44d58ccc0973";
    protected static final String validButNonexistAccessPolicyId = "nb:pid:UUID:38dcb3a0-ef64-4ad0-bbb5-67a14c6df2f7";
    protected static final String validButNonexistLocatorId = "nb:lid:UUID:92a70402-fca9-4aa3-80d7-d4de3792a27a";

    protected static final String invalidId = "notAValidId";

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @BeforeClass
    public static void setup() throws Exception {
        config = Configuration.getInstance();
        overrideWithEnv(config, MediaConfiguration.URI);
        overrideWithEnv(config, MediaConfiguration.OAUTH_URI);
        overrideWithEnv(config, MediaConfiguration.OAUTH_CLIENT_ID);
        overrideWithEnv(config, MediaConfiguration.OAUTH_CLIENT_SECRET);
        overrideWithEnv(config, MediaConfiguration.OAUTH_SCOPE);

        // TODO: Replace with call to MediaService.create once that's updated
        service = MediaService.create(config);

        cleanupEnvironment();
    }

    protected static void overrideWithEnv(Configuration config, String key) {
        String value = System.getenv(key);
        if (value == null)
            return;

        config.setProperty(key, value);
    }

    @AfterClass
    public static void cleanup() throws Exception {
        cleanupEnvironment();
    }

    private static void cleanupEnvironment() {
        // TODO: This should be removed once cascade delete is implemented for Assets.
        // But for now, trying to delete an asset with fail if there are any 
        // existing Locators associated with it.
        removeAllTestLocators();
        removeAllTestAssets();
        removeAllTestAccessPolicies();
        removeAllTestContentKeys();
    }

    private static void removeAllTestContentKeys() {
        try {
            List<ContentKeyInfo> contentKeyInfos = service.list(ContentKey.list());

            for (ContentKeyInfo contentKeyInfo : contentKeyInfos) {
                service.delete(ContentKey.delete(contentKeyInfo.getId()));
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void removeAllTestAccessPolicies() {
        try {
            List<AccessPolicyInfo> policies = service.list(AccessPolicy.list());

            for (AccessPolicyInfo policy : policies) {
                if (policy.getName().startsWith(testPolicyPrefix)) {
                    service.delete(AccessPolicy.delete(policy.getId()));
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void removeAllTestAssets() {
        try {
            List<AssetInfo> listAssetsResult = service.list(Asset.list());
            for (AssetInfo assetInfo : listAssetsResult) {
                if (assetInfo.getName().startsWith(testAssetPrefix)) {
                    service.delete(Asset.delete(assetInfo.getId()));
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void removeAllTestLocators() {
        try {
            ListResult<LocatorInfo> listLocatorsResult = service.list(Locator.list());
            for (LocatorInfo locatorInfo : listLocatorsResult) {
                AssetInfo ai = service.get(Asset.get(locatorInfo.getAssetId()));
                if (ai.getName().startsWith(testAssetPrefix)) {
                    service.delete(Locator.delete(locatorInfo.getId()));
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    interface ComponentDelegate {
        void verifyEquals(String message, Object expected, Object actual);
    }

    protected <T> void verifyListResultContains(List<T> expectedInfos, Collection<T> actualInfos,
            ComponentDelegate delegate) {
        verifyListResultContains("", expectedInfos, actualInfos, delegate);
    }

    protected <T> void verifyListResultContains(String message, List<T> expectedInfos, Collection<T> actualInfos,
            ComponentDelegate delegate) {
        assertNotNull(message + ": actualInfos", actualInfos);
        assertTrue(message + ": actual size should be same size or larger than expected size",
                actualInfos.size() >= expectedInfos.size());

        List<T> orderedAndFilteredActualInfo = new ArrayList<T>();
        try {
            for (T expectedInfo : expectedInfos) {
                Method getId = expectedInfo.getClass().getMethod("getId");
                String expectedId = (String) getId.invoke(expectedInfo);
                for (T actualInfo : actualInfos) {
                    if (((String) getId.invoke(actualInfo)).equals(expectedId)) {
                        orderedAndFilteredActualInfo.add(actualInfo);
                        break;
                    }
                }
            }
        }
        catch (Exception e) {
            // Don't worry about problems here.
            e.printStackTrace();
        }

        assertEquals(message + ": actual filtered size should be same as expected size", expectedInfos.size(),
                orderedAndFilteredActualInfo.size());

        if (delegate != null) {
            for (int i = 0; i < expectedInfos.size(); i++) {
                delegate.verifyEquals(message + ": orderedAndFilteredActualInfo " + i, expectedInfos.get(i),
                        orderedAndFilteredActualInfo.get(i));
            }
        }
    }

    protected void assertDateApproxEquals(Date expected, Date actual) {
        assertDateApproxEquals("", expected, actual);
    }

    protected void assertDateApproxEquals(String message, Date expected, Date actual) {
        // Default allows for a 30 seconds difference in dates, for clock skew, network delays, etc.
        long deltaInMilliseconds = 30000;

        if (expected == null || actual == null) {
            assertEquals(message, expected, actual);
        }
        else {
            long diffInMilliseconds = Math.abs(expected.getTime() - actual.getTime());

            // TODO: Remove this time-zone workaround when fixed:
            // https://github.com/WindowsAzure/azure-sdk-for-java-pr/issues/413
            if (diffInMilliseconds > deltaInMilliseconds) {
                // Just hard-code time-zone offset of 8 hours for now.
                diffInMilliseconds = Math.abs(diffInMilliseconds - 8 * 60 * 60 * 1000);
            }

            if (diffInMilliseconds > deltaInMilliseconds) {
                assertEquals(message, expected, actual);
            }
        }
    }
}
