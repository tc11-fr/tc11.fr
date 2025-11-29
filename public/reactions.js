/**
 * Article Reactions System for TC11.fr
 * 
 * Provides like and view tracking for articles.
 * Uses localStorage for persistence by default.
 * Can be extended with Supabase or other backends for cross-device persistence.
 * 
 * Options for static sites:
 * 1. localStorage (default) - Likes stored locally per browser
 * 2. Supabase - Free PostgreSQL database with REST API (requires configuration)
 * 
 * Configuration in templates/partials/head.html:
 * <script>
 *   window.TC11_REACTIONS_CONFIG = {
 *     backend: 'localStorage', // or 'supabase'
 *     supabaseUrl: 'https://your-project.supabase.co',
 *     supabaseAnonKey: 'your-anon-key'
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
      supabaseAnonKey: null
    }, window.TC11_REACTIONS_CONFIG || {});
  }

  /**
   * Get article ID from current URL
   */
  function getArticleId() {
    const path = window.location.pathname;
    // Remove trailing slash and return
    return path.replace(/\/$/, '') || '/';
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
        // First try to get current views
        const { data: existing } = await this.supabase
          .from('article_reactions')
          .select('views')
          .eq('article_id', articleId)
          .single();
        
        const newViews = (existing?.views || 0) + 1;
        
        const { error } = await this.supabase
          .from('article_reactions')
          .upsert({ article_id: articleId, views: newViews }, { onConflict: 'article_id' });
        
        if (error) throw error;
        return newViews;
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
   * Get the appropriate storage adapter based on configuration
   */
  function getAdapter() {
    const config = getConfig();
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
   * Initialize the reactions system
   */
  async function initReactions() {
    const articleId = getArticleId();
    const adapter = getAdapter();

    // Find reaction containers
    const likeButton = document.getElementById('like-button');
    const likeCount = document.getElementById('like-count');
    const viewCount = document.getElementById('view-count');

    // Track view (only once per session per article)
    const sessionViewsKey = 'tc11_session_views';
    let sessionViews = [];
    try {
      sessionViews = JSON.parse(sessionStorage.getItem(sessionViewsKey) || '[]');
    } catch (e) {
      sessionViews = [];
    }

    if (viewCount && !sessionViews.includes(articleId)) {
      const views = await adapter.incrementViews(articleId);
      viewCount.textContent = formatCount(views);
      sessionViews.push(articleId);
      sessionStorage.setItem(sessionViewsKey, JSON.stringify(sessionViews));
    } else if (viewCount) {
      const views = await adapter.getViews(articleId);
      viewCount.textContent = formatCount(views);
    }

    // Initialize like button
    if (likeButton && likeCount) {
      const likes = await adapter.getLikes(articleId);
      const userHasLiked = adapter.hasUserLiked(articleId);

      likeCount.textContent = formatCount(likes);
      
      if (userHasLiked) {
        likeButton.classList.add('liked');
        likeButton.setAttribute('aria-pressed', 'true');
      }

      likeButton.addEventListener('click', async function(e) {
        e.preventDefault();
        
        const isCurrentlyLiked = adapter.hasUserLiked(articleId);
        const currentLikes = await adapter.getLikes(articleId);
        const newLikes = isCurrentlyLiked ? Math.max(0, currentLikes - 1) : currentLikes + 1;

        await adapter.setLikes(articleId, newLikes);
        adapter.setUserLiked(articleId, !isCurrentlyLiked);

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
