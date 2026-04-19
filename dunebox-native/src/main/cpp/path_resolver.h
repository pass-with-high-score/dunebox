#ifndef DUNEBOX_PATH_RESOLVER_H
#define DUNEBOX_PATH_RESOLVER_H

#include <string>
#include <vector>
#include <mutex>
#include <unordered_map>

namespace dunebox {

    enum class IOAction {
        ALLOW,      // No rule matched, allow as-is
        REDIRECT,   // Redirect to a different path
        DENY        // Block access (return EACCES)
    };

    struct IOResult {
        IOAction action;
        std::string redirectedPath; // Only valid when action == REDIRECT
    };

    /**
     * Thread-safe path resolver for IO redirect rules.
     * Uses prefix matching to efficiently resolve paths.
     */
    class PathResolver {
    public:
        static PathResolver& getInstance();

        /**
         * Add a redirect rule: access to `fromPrefix` will be redirected to `toPrefix`.
         * Example: addRedirectRule("/data/data/com.example", "/data/data/app.pwhs.dunebox/virtual/0/com.example")
         */
        void addRedirectRule(const std::string& fromPrefix, const std::string& toPrefix);

        /**
         * Add a deny rule: access to `pathPrefix` will be blocked.
         * Example: addDenyRule("/proc/self/maps")
         */
        void addDenyRule(const std::string& pathPrefix);

        /**
         * Resolve a path against all registered rules.
         * Returns IOResult indicating what action to take.
         */
        IOResult resolve(const char* path) const;

        /**
         * Clear all rules (used when stopping redirect).
         */
        void clearRules();

        /**
         * Check if redirect is currently active.
         */
        bool isActive() const { return active_; }
        void setActive(bool active) { active_ = active; }

    private:
        PathResolver() = default;

        struct RedirectRule {
            std::string fromPrefix;
            std::string toPrefix;
        };

        struct DenyRule {
            std::string pathPrefix;
        };

        mutable std::mutex mutex_;
        std::vector<RedirectRule> redirectRules_;
        std::vector<DenyRule> denyRules_;
        bool active_ = false;
    };

} // namespace dunebox

#endif // DUNEBOX_PATH_RESOLVER_H
