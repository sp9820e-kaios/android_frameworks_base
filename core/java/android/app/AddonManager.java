package android.app;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.IBinder;
import android.util.ArrayMap;
import android.util.Log;
import android.util.SparseArray;

import dalvik.system.PathClassLoader;

import java.util.ArrayList;
import java.util.List;
import java.lang.ref.WeakReference;

/**
 * @hide
 */
public class AddonManager /* TODO extends IAddonManager */ {
    public static interface InitialCallback {
        public Class onCreateAddon(Context context, Class clazz);
    }

    private static final String LOGTAG = AddonManager.class.getSimpleName();
    private static final boolean DEBUG = true;

    private static final String NO_PLUGIN = "";

    private static AddonManager sInstance;

    //Feature object cache
    private static ArrayMap<String, WeakReference<Object>> sFeatureCache
            = new ArrayMap<String, WeakReference<Object>>();
    private static ArrayMap<String, WeakReference<Class>> sClassCache
            = new ArrayMap<String, WeakReference<Class>>();
    //Feature class name cache
    private static SparseArray<String> sIdMap = new SparseArray<String>();
    //The installed Addon package information list
    private  List<PackageInfo> mManagedList;

    private IBinder mService;

    /**
     * In the normal case, mContext is equals to sAppContext.
     * If the APP is shared one process with other apps, the two context
     * maybe differently, because the APP need to create AddonManager itself
     * by the AddonManager Constructor {@link AddonManager(Context context)}.
     */
    private Context mContext;
    private static Context sAppContext;
    static void attachApplication(Context context) {
        sAppContext = context;
    }

    /**
     * Get the default addonManager, its connect to the application which
     * context belong to.
     * @return Reture the addonmanager instance for host application.
     */
    public static AddonManager getDefault() {
        if (sInstance != null) return sInstance;
        sInstance = new AddonManager(sAppContext);
        return sInstance;
    }

    /* TODO for binder service */
    AddonManager(ContextImpl context, IBinder binder) {
        this(context);
        mContext = context;
        mService = binder;
    }

    /**
     * The constructor of AddonManager, App can use it to create its own
     * addonmanager.
     * @param context The context of the host application.
     */
    public AddonManager(Context context) {
        mContext = context;
        if (mManagedList == null) {
            mManagedList = manageFeatureList(context);
        }
    }

    /**
     * Get the target package information of this AddonManger.
     * @param context The context belong to the host APP
     * @return Return the package information list of Addon.
     */
    private static List<PackageInfo> manageFeatureList(Context context) {
        final PackageManager pms = context.getPackageManager();
        List<PackageInfo> infoList = new ArrayList<PackageInfo>();
        final List<PackageInfo> installedList
                = pms.getInstalledPackages(PackageManager.GET_META_DATA);
        if (installedList != null && !installedList.isEmpty()) {
            String currentPackageName = context.getPackageName();
            for (PackageInfo pkg : installedList) {
                // verify data valid
                if (pkg == null || pkg.applicationInfo == null || !pkg.applicationInfo.enabled || pkg.applicationInfo.metaData == null) continue;
                if (pkg.applicationInfo.metaData.containsKey("isFeatureAddon")) {
                    if (pkg.applicationInfo.metaData.containsKey("targetPackages") && pkg.applicationInfo.metaData.getString("targetPackages", "")
                            .contains(currentPackageName)) {
                        if (DEBUG) Log.d(LOGTAG, "-> manageFeatureList: The Plugin of <" +
                            currentPackageName + "> is <" + pkg + "> .");
                        infoList.add(pkg);
                    }
                }
            }
        } else {
            // Should not happen
        }
        return infoList;
    }

    private static final String getCachedId(int featureId, Context context) {
        String value = sIdMap.get(featureId);
        // The value being null means the cache has been not created.
        if (value == null) {
            value = context.getString(featureId, NO_PLUGIN);
            sIdMap.put(featureId, value);
        }
        return value;
    }

    /**
     * Get the cached feature object.
     * @param featureName The feature class name.
     * @return Reture the cached feature object or null if the feature
     * object has not cached before.
     */
    private static final Object getCachedFeature(String featureName) {
        if (featureName == null || NO_PLUGIN.equals(featureName)) return null;
        WeakReference<Object> feature = sFeatureCache.get(featureName);
        if (feature != null && feature.get() != null) {
            return feature.get();
        } else {
            sFeatureCache.remove(featureName);
            return null;
        }
    }

    /**
     *
     * Accept null object and return null
     */
     /*
    private static final Object getCachedClass(String className) {
        if (featureName == null || NO_PLUGIN.equals(className)) return null;
        WeakReference<Class> feature = sFeatureCache.get(className);
        if (feature != null && feature.get() != null) {
            return feature.get();
        } else {
            sFeatureCache.remove(featureName);
            return null;
        }
    }*/

    /**
     * Get the Addon feature object if it exists, if not return the defClazz
     * Object.
     * @param featureId The string ID of feature class name.
     * @param defClazz The defalut class in the host APP that be extended by Addon class.
     * @return Return the Addon class instance or DefClazz instance.
     */
    public Object getAddon(int featureId, Class defClazz) {
        String featureClassName = mContext.getString(featureId, NO_PLUGIN);
        if (DEBUG) {
            Log.d(LOGTAG, "-> getAddon: featureClassName:" + featureClassName);
            if (defClazz != null)
            Log.i(LOGTAG, "-> getAddon,The classloader of defClazz is " + defClazz.getClassLoader());
        }
        Object featureObject = null;
        // If not define or invalid, so no need to query the package manager
        if (NO_PLUGIN.equals(featureClassName)) {
            try {
                featureObject = defClazz.newInstance();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            for (PackageInfo pkg:mManagedList) {
                if (pkg.applicationInfo.metaData.getString("featureClassNames", "").contains(featureClassName)) {
                    ClassLoader loader = ApplicationLoaders.getDefault().getClassLoader(
                            pkg.applicationInfo.sourceDir, null, mContext.getClassLoader());
                    Class<?> clazz = null;
                    try {
                        clazz = loader.loadClass(featureClassName);
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                    if (DEBUG) Log.d(LOGTAG, "->getAddon, createAddon pkg: " + pkg + ", clazz: " +
                                        clazz+ ", classloader:" + loader);
                    featureObject = createAddon(pkg, clazz);

                    break;
                }
            }
        }
        if (featureObject == null) {
            try {
                if (DEBUG) Log.d(LOGTAG, "->getAddon: Create feature <" + featureClassName +
                        "> object Failed," + " AddonManager will return <" + defClazz + "> object.");
                if (defClazz != null) featureObject = defClazz.newInstance();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return featureObject;
    }

    private Object createAddon(PackageInfo pkg, Class clazz) {
        Object featureObject = null;
        if (clazz != null) {
            try {
                featureObject = clazz.newInstance();
                if (featureObject instanceof InitialCallback) {
                    InitialCallback callback = (InitialCallback) featureObject;
                    Context pluginContext = null;
                    try {
                        pluginContext = sAppContext.createPackageContext(pkg.applicationInfo.packageName,
                                Context.CONTEXT_IGNORE_SECURITY | Context.CONTEXT_INCLUDE_CODE);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    Class wantedClass = callback.onCreateAddon(pluginContext, clazz);
                    if (DEBUG) Log.d(LOGTAG, "->createAddon, wantedClass <" + wantedClass +
                            ">, pluginContext: " + pluginContext);
                    if (wantedClass != null && !wantedClass.equals(clazz)) {
                        featureObject = createAddon(pkg, wantedClass);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return featureObject;
    }

    public boolean isFeatureEnabled(int featureId) {
        // TODO should be remote and be controlled
        return true;
    }

    public boolean enableFeature(int featureId) {
        // TODO should be remote and be controlled
        return true;
    }
}
