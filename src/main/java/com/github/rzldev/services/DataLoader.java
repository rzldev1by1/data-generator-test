package com.github.rzldev.services;

import com.github.rzldev.configs.InMemoryCatalogueConfig;
import com.github.rzldev.schemas.*;
import io.quarkus.runtime.StartupEvent;
import org.apache.commons.io.IOUtils;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.commons.configuration.XMLStringConfiguration;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@ApplicationScoped
public class DataLoader {

    private static final Logger LOGGER = Logger.getLogger(DataLoader.class);

    @Inject
    RemoteCacheManager cacheManager;

    @Inject
    InMemoryCatalogueConfig inMemoryCatalogueConfig;

    private int totalPerCache = 1000;

    void onStart(@Observes StartupEvent event) {
        long start = new Date().getTime();
        try {
            LOGGER.info("Creating caches");

            URL tableStoreCacheConfig = this.getClass().getClassLoader().getResource("META-INF/protobufs/tableStore.xml");
            String bookConfigTable = replaceDBConnectionConfiguration(tableStoreCacheConfig, "books", BookKey.class, "Book");
            String authorConfigTable = replaceDBConnectionConfiguration(tableStoreCacheConfig, "authors", AuthorKey.class, "Author");

            RemoteCache<AuthorKey, Author> authorCache = cacheManager.administration().getOrCreateCache(inMemoryCatalogueConfig.authorCacheName(),
                    new XMLStringConfiguration(authorConfigTable));

            RemoteCache<BookKey, Book> bookCache = cacheManager.administration().getOrCreateCache(inMemoryCatalogueConfig.bookCacheName(),
                    new XMLStringConfiguration(bookConfigTable));

            LOGGER.info("Cleaning up past caches");
            cleanupCaches(authorCache, bookCache);
            LOGGER.info("Loading caches");
            loadData(authorCache, bookCache);
        } catch (IOException e) {
            LOGGER.error("IOException", e);
        } finally {
            LOGGER.info("Finished in " + (new Date().getTime() - start) + " ms");
        }
    }

    private void loadData(RemoteCache<AuthorKey, Author> authorCache, RemoteCache<BookKey, Book> bookCache) {
        List<Author> authors = null;
        try {
            authors = generateAuthorList(authorCache);
            generateBookList(authors, bookCache);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void generateBookList(List<Author> authors, RemoteCache<BookKey, Book> bookCache) throws InterruptedException {
        for (int x = 1; x <= totalPerCache; x++) {
            Author a = authors.get(x - 1);
            Book b = new Book(x, "book " + x, "Description of book " + x, 2000, a.getId(), "3.99");
            BookKey bk = new BookKey(x, a.getId());
            bookCache.put(bk, b);
            Thread.sleep(10);
            LOGGER.debug(b.getTitle());
        }
    }

    private List<Author> generateAuthorList(RemoteCache<AuthorKey, Author> authorCache) throws InterruptedException {
        ArrayList<Author> authors = new ArrayList<>(10000);
        for (int x = 1; x <= totalPerCache; x++) {
            Author a = new Author(x, "author " + x, "author " + x);
            AuthorKey ak = new AuthorKey(x);
            authors.add(a);
            authorCache.put(ak, a);
            Thread.sleep(10);
            LOGGER.debug(a.getName());
        }
        return authors;
    }

    private void cleanupCaches(RemoteCache<AuthorKey, Author> authors, RemoteCache<BookKey, Book> books) {
        try {
            CompletableFuture.allOf(authors.clearAsync(), books.clearAsync()).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            LOGGER.error("Something went wrong while cleaning the data.", e);
        }
    }


    private String replaceDBConnectionConfiguration(URL cacheConfig, String table, Class<? extends MessageKey> key, String message) throws IOException {
        String config = IOUtils.toString(cacheConfig, StandardCharsets.UTF_8)
                .replace("TABLE_NAME", table)
                .replace("MESSAGE_KEY", key.getSimpleName())
                .replace("MESSAGE_NAME", message)
                .replace("CONNECTION_URL", inMemoryCatalogueConfig.connectionUrl())
                .replace("USERNAME", inMemoryCatalogueConfig.username())
                .replace("PASSWORD", inMemoryCatalogueConfig.password())
                .replace("DIALECT", inMemoryCatalogueConfig.dialect())
                .replace("DRIVER", inMemoryCatalogueConfig.driver());
        LOGGER.info(config);
        return config;
    }
}
