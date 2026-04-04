package com.app.shahbaztrades.service.impl;

import com.app.shahbaztrades.model.entity.DatabaseCounter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SequenceGeneratorService {

    private final MongoTemplate mongoTemplate;

    public long getNextSequence(String sequenceName) {
        Query query = new Query(Criteria.where("_id").is(sequenceName));

        Update update = new Update().inc("seq", 1);

        FindAndModifyOptions options = new FindAndModifyOptions()
                .returnNew(true)
                .upsert(true);

        DatabaseCounter result = mongoTemplate.findAndModify(query, update, options, DatabaseCounter.class);

        return (result != null) ? result.getSeq() : 1;
    }

}
