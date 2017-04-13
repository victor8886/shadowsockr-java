package com.github.shadowsocks.database;

import com.github.shadowsocks.utils.SS_SDK;

import java.sql.SQLException;
import java.util.List;

/**
 * Created by victor on 2017/4/6.
 */

public class ProfileManager {
    SS_SDK app = SS_SDK.getInstance();
    private DBHelper dbHelper;
    private Profile profileAddedListener;

    public void setProfileAddedListener(Profile listener) {
        profileAddedListener = listener;
    }
    public ProfileManager(DBHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public Profile createProfile(Profile p) {
        Profile profile = p;
        if (p == null) {
            profile = new Profile();
        }
        profile.id = 0;
        Profile oldProfile = app.currentProfile();
        try {
            if (null != oldProfile) {
                //复制属性
                profile.route = oldProfile.route;
                profile.ipv6 = oldProfile.ipv6;
                profile.proxyApps = oldProfile.proxyApps;
                profile.bypass = oldProfile.bypass;
                profile.individual = oldProfile.individual;
                profile.udpdns = oldProfile.udpdns;
                profile.dns = oldProfile.dns;
                profile.china_dns = oldProfile.china_dns;
            }
            String[] last = dbHelper.profileDao.queryRaw(dbHelper.profileDao.queryBuilder()
                    .selectRaw("MAX(userOrder)")
                    .prepareStatementString()).getFirstResult();
            if (last != null && last.length == 1 && last[0] != null) {
                profile.userOrder = Long.decode(last[0])+1;
            }
            Profile last_exist = dbHelper.profileDao.queryBuilder()
                    .where().eq("name", profile.name)
                    .and().eq("host", profile.host)
                    .and().eq("remotePort", profile.remotePort)
                    .and().eq("password", profile.password)
                    .and().eq("protocol", profile.protocol)
                    .and().eq("protocol_param", profile.protocol_param)
                    .and().eq("obfs", profile.obfs)
                    .and().eq("obfs_param", profile.obfs_param)
                    .and().eq("method", profile.method)
                    .queryForFirst();
            if (last_exist == null) {
                dbHelper.profileDao.create(profile);
                if (profileAddedListener != null) {
                    profileAddedListener = profile;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return profile;
    }

    public boolean updateProfile(Profile profile) {
        try {
             dbHelper.profileDao.update(profile);
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public Profile getProfile(int id) {
        try {
            Profile profile = dbHelper.profileDao.queryForId(id);
            return profile;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean delProfile(int id) {
        try {
            dbHelper.profileDao.deleteById(id);
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public Profile getFirstProfile() {
        try {
            List<Profile> profiles = dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder().limit(1L).prepare());
            if (profiles.size() == 1) {
                return profiles.get(0);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
        return null;
    }

    public List<Profile> getAllProfiles(){
        try {
            List<Profile> profiles = dbHelper.profileDao.query(dbHelper.profileDao.queryBuilder().orderBy("userOrder", true).prepare());
            return profiles;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Profile createDefault() {
        Profile profile = new Profile();
        profile.name = "Default";
        profile.host = "198.199.101.152";
        profile.remotePort = 443;
        profile.password = "u1rRWTssNv0p";
        createProfile(profile);
        return profile;
    }



}
