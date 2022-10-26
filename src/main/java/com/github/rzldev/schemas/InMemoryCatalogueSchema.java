package com.github.rzldev.schemas;

import org.infinispan.protostream.SerializationContextInitializer;
import org.infinispan.protostream.annotations.AutoProtoSchemaBuilder;

@AutoProtoSchemaBuilder(
        schemaPackageName = "book_samples",
        includeClasses = {Author.class, AuthorKey.class, Book.class, BookKey.class}
)
public interface InMemoryCatalogueSchema extends SerializationContextInitializer {
}
