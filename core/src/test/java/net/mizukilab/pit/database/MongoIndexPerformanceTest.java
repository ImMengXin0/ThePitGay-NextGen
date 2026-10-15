package net.mizukilab.pit.database;

import cn.charlotte.pit.ThePit;
import com.mongodb.client.*;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.Test;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MongoIndexPerformanceTest {
    @SuppressWarnings("unchecked")
    private MongoCollection<Document> collection(Document... indexes) {
        MongoCollection<Document> collection=mock(MongoCollection.class);
        ListIndexesIterable<Document> list=mock(ListIndexesIterable.class);
        when(collection.listIndexes()).thenReturn(list);
        when(list.iterator()).thenAnswer(call -> {
            Iterator<Document> iterator=Arrays.asList(indexes).iterator();
            MongoCursor<Document> cursor=mock(MongoCursor.class);
            when(cursor.hasNext()).thenAnswer(c -> iterator.hasNext());
            when(cursor.next()).thenAnswer(c -> iterator.next()); return cursor;
        });
        return collection;
    }
    private void ensure(MongoCollection<Document> collection,String field) {
        try(var pit=mockStatic(ThePit.class)) {
            ThePit plugin=mock(ThePit.class); when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
            pit.when(ThePit::getInstance).thenReturn(plugin);
            MongoDB.ensureTimeIndex(collection,field);
        }
    }
    @Test public void existingAscendingOrDescendingIndexesWithAnyNameAreReused() {
        for(int direction:new int[]{-1,1}) {
            var collection=collection(new Document("key",new Document("timeStamp",direction)).append("name","legacy"));
            ensure(collection,"timeStamp"); verify(collection,never()).createIndex(any(Bson.class));
        }
    }
    @Test public void missingIndexesUseCorrectCollectionAndField() {
        var trade=collection(new Document("key",new Document("timeStamp",1)));
        var inv=collection(); ensure(trade,"completeTime"); ensure(inv,"timeStamp");
        var captor=org.mockito.ArgumentCaptor.forClass(Bson.class);
        verify(trade).createIndex(captor.capture());
        assertTrue(captor.getValue().toBsonDocument(Document.class,com.mongodb.MongoClientSettings.getDefaultCodecRegistry()).containsKey("completeTime"));
        verify(inv).createIndex(captor.capture());
        assertTrue(captor.getValue().toBsonDocument(Document.class,com.mongodb.MongoClientSettings.getDefaultCodecRegistry()).containsKey("timeStamp"));
    }
    @Test public void partialSparseAndCompoundIndexesDoNotSuppressRequiredFullIndex() {
        var collection=collection(
                new Document("key",new Document("timeStamp",1)).append("sparse",true),
                new Document("key",new Document("timeStamp",1)).append("partialFilterExpression",new Document("uuid","test")),
                new Document("key",new Document("uuid",1).append("timeStamp",1)));
        ensure(collection,"timeStamp"); verify(collection).createIndex(any(Bson.class));
    }
}
