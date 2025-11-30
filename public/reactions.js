/**
 * Article Reactions System for TC11.fr
 * 
 * Provides like and view tracking for articles.
 * Uses localStorage for persistence by default.
 * Can be extended with Supabase, Giscus or other backends for cross-device persistence.
 * 
 * Options for static sites:
 * 1. localStorage (default) - Likes stored locally per browser
 * 2. Supabase - Free PostgreSQL database with REST API (requires configuration)
 * 3. Giscus - GitHub Discussions-based reactions and comments (requires GitHub repo)
 * 
 * Configuration in templates/partials/head.html:
 * <script>
 *   window.TC11_REACTIONS_CONFIG = {
 *     backend: 'localStorage', // or 'supabase' or 'giscus'
 *     supabaseUrl: 'https://your-project.supabase.co',
 *     supabaseAnonKey: 'your-anon-key',
 *     // Giscus configuration (get from https://giscus.app)
 *     giscusRepo: 'tc11-fr/tc11.fr',
 *     giscusRepoId: 'R_kgDOPa7m9g',
 *     giscusCategory: 'Announcements',
 *     giscusCategoryId: 'DIC_kwDOPa7m9s4CzNU1',
 *     giscusMapping: 'pathname', // or 'url', 'title', 'og:title', 'specific', 'number'
 *     giscusTheme: 'preferred_color_scheme', // or 'light', 'dark', custom URL
 *     giscusLang: 'fr'
 *   };
 * </script>
 */

(function() {
  'use strict';

  const STORAGE_KEY_LIKES = 'tc11_article_likes';
  const STORAGE_KEY_VIEWS = 'tc11_article_views';
  const STORAGE_KEY_LIKED = 'tc11_user_liked';

  /**
   * Get configuration from window object or use defaults
   */
  function getConfig() {
    return Object.assign({
      backend: 'localStorage',
      supabaseUrl: null,
      supabaseAnonKey: null,
      // Giscus configuration - TC11 defaults
      giscusRepo: 'tc11-fr/tc11.fr',
      giscusRepoId: 'R_kgDOPa7m9g',
      giscusCategory: 'Announcements',
      giscusCategoryId: 'DIC_kwDOPa7m9s4CzNU1',
      giscusMapping: 'pathname',
      giscusTheme: 'preferred_color_scheme',
      giscusLang: 'fr'
    }, window.TC11_REACTIONS_CONFIG || {});
  }

  /**
   * Get article ID from current URL
   * Uses the full pathname to ensure unique IDs for each article
   */
  function getArticleId() {
    const path = window.location.pathname;
    // Remove trailing slash but keep the full path for unique identification
    const normalizedPath = path.replace(/\/$/, '');
    // Return the normalized path or a fallback for the homepage
    return normalizedPath || 'homepage';
  }

  /**
   * Storage adapter for localStorage
   */
  const localStorageAdapter = {
    async getLikes(articleId) {
      try {
        const data = JSON.parse(localStorage.getItem(STORAGE_KEY_LIKES) || '{}');
        return data[articleId] || 0;
      } catch (e) {
        console.error('Error reading likes:', e);
        return 0;
      }
    },

    async setLikes(articleId, count) {
      try {
        const data = JSON.parse(localStorage.getItem(STORAGE_KEY_LIKES) || '{}');
        data[articleId] = count;
        localStorage.setItem(STORAGE_KEY_LIKES, JSON.stringify(data));
      } catch (e) {
        console.error('Error saving likes:', e);
      }
    },

    async getViews(articleId) {
      try {
        const data = JSON.parse(localStorage.getItem(STORAGE_KEY_VIEWS) || '{}');
        return data[articleId] || 0;
      } catch (e) {
        console.error('Error reading views:', e);
        return 0;
      }
    },

    async incrementViews(articleId) {
      try {
        const data = JSON.parse(localStorage.getItem(STORAGE_KEY_VIEWS) || '{}');
        data[articleId] = (data[articleId] || 0) + 1;
        localStorage.setItem(STORAGE_KEY_VIEWS, JSON.stringify(data));
        return data[articleId];
      } catch (e) {
        console.error('Error incrementing views:', e);
        return 0;
      }
    },

    hasUserLiked(articleId) {
      try {
        const liked = JSON.parse(localStorage.getItem(STORAGE_KEY_LIKED) || '[]');
        return liked.includes(articleId);
      } catch (e) {
        return false;
      }
    },

    setUserLiked(articleId, liked) {
      try {
        const likedList = JSON.parse(localStorage.getItem(STORAGE_KEY_LIKED) || '[]');
        if (liked && !likedList.includes(articleId)) {
          likedList.push(articleId);
        } else if (!liked) {
          const index = likedList.indexOf(articleId);
          if (index > -1) likedList.splice(index, 1);
        }
        localStorage.setItem(STORAGE_KEY_LIKED, JSON.stringify(likedList));
      } catch (e) {
        console.error('Error saving user liked status:', e);
      }
    }
  };

  /**
   * Storage adapter for Supabase
   * Requires a table 'article_reactions' with columns:
   * - article_id (text, primary key)
   * - likes (integer, default 0)
   * - views (integer, default 0)
   */
  const supabaseAdapter = {
    supabase: null,

    init() {
      const config = getConfig();
      if (config.supabaseUrl && config.supabaseAnonKey && window.supabase) {
        this.supabase = window.supabase.createClient(config.supabaseUrl, config.supabaseAnonKey);
        return true;
      }
      console.warn('Supabase not configured, falling back to localStorage');
      return false;
    },

    async getLikes(articleId) {
      if (!this.supabase) return localStorageAdapter.getLikes(articleId);
      
      try {
        const { data, error } = await this.supabase
          .from('article_reactions')
          .select('likes')
          .eq('article_id', articleId)
          .single();
        
        if (error && error.code !== 'PGRST116') throw error;
        return data?.likes || 0;
      } catch (e) {
        console.error('Supabase error getting likes:', e);
        return localStorageAdapter.getLikes(articleId);
      }
    },

    async setLikes(articleId, count) {
      if (!this.supabase) return localStorageAdapter.setLikes(articleId, count);
      
      try {
        const { error } = await this.supabase
          .from('article_reactions')
          .upsert({ article_id: articleId, likes: count }, { onConflict: 'article_id' });
        
        if (error) throw error;
        // Also store locally for user liked status
        localStorageAdapter.setLikes(articleId, count);
      } catch (e) {
        console.error('Supabase error setting likes:', e);
        localStorageAdapter.setLikes(articleId, count);
      }
    },

    async getViews(articleId) {
      if (!this.supabase) return localStorageAdapter.getViews(articleId);
      
      try {
        const { data, error } = await this.supabase
          .from('article_reactions')
          .select('views')
          .eq('article_id', articleId)
          .single();
        
        if (error && error.code !== 'PGRST116') throw error;
        return data?.views || 0;
      } catch (e) {
        console.error('Supabase error getting views:', e);
        return localStorageAdapter.getViews(articleId);
      }
    },

    async incrementViews(articleId) {
      if (!this.supabase) return localStorageAdapter.incrementViews(articleId);
      
      try {
        // Use RPC for atomic increment to avoid race conditions
        // This requires a Supabase function: increment_views(article_id_param TEXT)
        // If RPC is not available, fall back to upsert with current timestamp for uniqueness
        const { data, error } = await this.supabase
          .rpc('increment_views', { article_id_param: articleId });
        
        if (error) {
          // Fallback: use upsert if RPC is not available
          // Note: This has a potential race condition but is better than nothing
          console.warn('RPC not available, falling back to upsert');
          const { data: existing } = await this.supabase
            .from('article_reactions')
            .select('views')
            .eq('article_id', articleId)
            .single();
          
          const newViews = (existing?.views || 0) + 1;
          
          await this.supabase
            .from('article_reactions')
            .upsert({ article_id: articleId, views: newViews }, { onConflict: 'article_id' });
          
          return newViews;
        }
        
        return data || 1;
      } catch (e) {
        console.error('Supabase error incrementing views:', e);
        return localStorageAdapter.incrementViews(articleId);
      }
    },

    // User liked status is always stored locally (privacy-friendly)
    hasUserLiked(articleId) {
      return localStorageAdapter.hasUserLiked(articleId);
    },

    setUserLiked(articleId, liked) {
      localStorageAdapter.setUserLiked(articleId, liked);
    }
  };

  /**
   * Giscus adapter - uses GitHub Discussions for reactions and comments
   * When Giscus is enabled, the built-in like/view UI is hidden and Giscus widget is shown instead
   */
  const giscusAdapter = {
    initialized: false,

    init() {
      const config = getConfig();
      if (!config.giscusRepo || !config.giscusRepoId || !config.giscusCategoryId) {
        console.warn('Giscus not configured, falling back to localStorage');
        return false;
      }
      this.initialized = true;
      return true;
    },

    /**
     * Inject Giscus widget into the page
     */
    injectGiscusWidget() {
      const config = getConfig();
      const container = document.getElementById('giscus-container');
      
      if (!container) {
        console.warn('Giscus container not found');
        return;
      }

      // Create Giscus script
      const script = document.createElement('script');
      script.src = 'https://giscus.app/client.js';
      script.setAttribute('data-repo', config.giscusRepo);
      script.setAttribute('data-repo-id', config.giscusRepoId);
      script.setAttribute('data-category', config.giscusCategory || 'Announcements');
      script.setAttribute('data-category-id', config.giscusCategoryId);
      script.setAttribute('data-mapping', config.giscusMapping || 'pathname');
      script.setAttribute('data-strict', '0');
      script.setAttribute('data-reactions-enabled', '1');
      script.setAttribute('data-emit-metadata', '0');
      script.setAttribute('data-input-position', 'bottom');
      script.setAttribute('data-theme', config.giscusTheme || 'preferred_color_scheme');
      script.setAttribute('data-lang', config.giscusLang || 'fr');
      script.crossOrigin = 'anonymous';
      script.async = true;

      container.appendChild(script);
    },

    /**
     * Giscus handles its own reactions via GitHub Discussions reactions.
     * Likes are managed entirely by Giscus widget (users react via GitHub).
     * Views are still tracked locally for analytics purposes since Giscus
     * doesn't provide view tracking functionality.
     */
    async getLikes() { return 0; },
    async setLikes() {},
    async getViews(articleId) { return localStorageAdapter.getViews(articleId); },
    async incrementViews(articleId) { return localStorageAdapter.incrementViews(articleId); },
    hasUserLiked() { return false; },
    setUserLiked() {}
  };

  /**
   * Get the appropriate storage adapter based on configuration
   */
  function getAdapter() {
    const config = getConfig();
    if (config.backend === 'giscus') {
      if (giscusAdapter.init()) {
        return giscusAdapter;
      }
    }
    if (config.backend === 'supabase') {
      if (supabaseAdapter.init()) {
        return supabaseAdapter;
      }
    }
    return localStorageAdapter;
  }

  /**
   * Format number for display (e.g., 1000 -> 1k)
   */
  function formatCount(count) {
    if (count >= 1000000) {
      return (count / 1000000).toFixed(1).replace(/\.0$/, '') + 'M';
    }
    if (count >= 1000) {
      return (count / 1000).toFixed(1).replace(/\.0$/, '') + 'k';
    }
    return count.toString();
  }

  /**
   * Track article view (once per session per article)
   * @param {string} articleId - The article identifier
   * @param {object} adapter - The storage adapter to use
   * @param {HTMLElement|null} viewCountElement - Optional element to update with view count
   * @returns {Promise<boolean>} - Whether a new view was tracked
   */
  async function trackView(articleId, adapter, viewCountElement) {
    const sessionViewsKey = 'tc11_session_views';
    let sessionViews = [];
    try {
      sessionViews = JSON.parse(sessionStorage.getItem(sessionViewsKey) || '[]');
    } catch (e) {
      sessionViews = [];
    }

    if (!sessionViews.includes(articleId)) {
      const views = await adapter.incrementViews(articleId);
      if (viewCountElement) {
        viewCountElement.textContent = formatCount(views);
      }
      sessionViews.push(articleId);
      sessionStorage.setItem(sessionViewsKey, JSON.stringify(sessionViews));
      return true;
    } else if (viewCountElement) {
      const views = await adapter.getViews(articleId);
      viewCountElement.textContent = formatCount(views);
    }
    return false;
  }

  /**
   * Initialize the reactions system
   */
  async function initReactions() {
    const config = getConfig();
    const articleId = getArticleId();
    const adapter = getAdapter();

    // Find reaction containers
    const likeButton = document.getElementById('like-button');
    const likeCount = document.getElementById('like-count');
    const viewCount = document.getElementById('view-count');
    const reactionsGroup = document.querySelector('[role="group"][aria-label="Réactions de l\'article"]');
    const giscusContainer = document.getElementById('giscus-container');

    // If Giscus is enabled, hide built-in reactions and show Giscus widget
    if (config.backend === 'giscus' && giscusAdapter.initialized) {
      // Hide the built-in reactions UI
      if (reactionsGroup) {
        reactionsGroup.style.display = 'none';
      }
      // Show and initialize Giscus
      if (giscusContainer) {
        giscusContainer.style.display = 'block';
        giscusAdapter.injectGiscusWidget();
      }
      
      // Still track views locally for analytics (without UI update)
      await trackView(articleId, adapter, null);
      return;
    }

    // Track view with UI update
    await trackView(articleId, adapter, viewCount);

    // Initialize like button
    if (likeButton && likeCount) {
      // Cache the initial like count to avoid refetching on every click
      let cachedLikes = await adapter.getLikes(articleId);
      const userHasLiked = adapter.hasUserLiked(articleId);

      likeCount.textContent = formatCount(cachedLikes);
      
      if (userHasLiked) {
        likeButton.classList.add('liked');
        likeButton.setAttribute('aria-pressed', 'true');
      }

      likeButton.addEventListener('click', async function(e) {
        e.preventDefault();
        
        const isCurrentlyLiked = adapter.hasUserLiked(articleId);
        // Use cached value instead of fetching again
        const newLikes = isCurrentlyLiked ? Math.max(0, cachedLikes - 1) : cachedLikes + 1;

        await adapter.setLikes(articleId, newLikes);
        adapter.setUserLiked(articleId, !isCurrentlyLiked);
        
        // Update cache
        cachedLikes = newLikes;

        likeCount.textContent = formatCount(newLikes);
        
        if (!isCurrentlyLiked) {
          likeButton.classList.add('liked');
          likeButton.setAttribute('aria-pressed', 'true');
          // Add animation class
          likeButton.classList.add('like-animate');
          setTimeout(() => likeButton.classList.remove('like-animate'), 300);
        } else {
          likeButton.classList.remove('liked');
          likeButton.setAttribute('aria-pressed', 'false');
        }
      });
    }
  }

  // Initialize when DOM is ready
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initReactions);
  } else {
    initReactions();
  }

  // Export for external use
  window.TC11Reactions = {
    getArticleId,
    getAdapter,
    formatCount,
    init: initReactions
  };
})();
