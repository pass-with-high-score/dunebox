#include "path_resolver.h"
#include <algorithm>
#include <android/log.h>

#define LOG_TAG "DuneBox-Native"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace dunebox {

    PathResolver& PathResolver::getInstance() {
        static PathResolver instance;
        return instance;
    }

    void PathResolver::addRedirectRule(const std::string& fromPrefix, const std::string& toPrefix) {
        std::lock_guard<std::mutex> lock(mutex_);
        // Check for duplicate
        for (const auto& rule : redirectRules_) {
            if (rule.fromPrefix == fromPrefix) {
                LOGD("Redirect rule already exists for: %s, updating to: %s", fromPrefix.c_str(), toPrefix.c_str());
                return;
            }
        }
        redirectRules_.push_back({fromPrefix, toPrefix});
        LOGD("Added redirect rule: %s -> %s", fromPrefix.c_str(), toPrefix.c_str());
    }

    void PathResolver::addDenyRule(const std::string& pathPrefix) {
        std::lock_guard<std::mutex> lock(mutex_);
        for (const auto& rule : denyRules_) {
            if (rule.pathPrefix == pathPrefix) {
                LOGD("Deny rule already exists for: %s", pathPrefix.c_str());
                return;
            }
        }
        denyRules_.push_back({pathPrefix});
        LOGD("Added deny rule: %s", pathPrefix.c_str());
    }

    IOResult PathResolver::resolve(const char* path) const {
        if (!path || !active_) {
            return {IOAction::ALLOW, ""};
        }

        std::lock_guard<std::mutex> lock(mutex_);
        std::string pathStr(path);

        // Check deny rules first (higher priority)
        for (const auto& rule : denyRules_) {
            if (pathStr.find(rule.pathPrefix) == 0 || pathStr == rule.pathPrefix) {
                LOGD("IO DENY: %s", path);
                return {IOAction::DENY, ""};
            }
        }

        // Check redirect rules — find longest prefix match
        const RedirectRule* bestMatch = nullptr;
        size_t bestMatchLength = 0;

        for (const auto& rule : redirectRules_) {
            if (pathStr.find(rule.fromPrefix) == 0 && rule.fromPrefix.length() > bestMatchLength) {
                bestMatch = &rule;
                bestMatchLength = rule.fromPrefix.length();
            }
        }

        if (bestMatch) {
            std::string redirected = bestMatch->toPrefix + pathStr.substr(bestMatchLength);
            LOGD("IO REDIRECT: %s -> %s", path, redirected.c_str());
            return {IOAction::REDIRECT, redirected};
        }

        return {IOAction::ALLOW, ""};
    }

    void PathResolver::clearRules() {
        std::lock_guard<std::mutex> lock(mutex_);
        redirectRules_.clear();
        denyRules_.clear();
        active_ = false;
        LOGD("All IO rules cleared");
    }

} // namespace dunebox
