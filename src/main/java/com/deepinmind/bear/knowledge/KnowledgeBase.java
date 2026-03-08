package com.deepinmind.bear.knowledge;

import com.deepinmind.bear.oss.OSSService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeBase {

    @Autowired
    OSSService ossService;

    @Autowired
    KnowledgeBaseUpdate knowledgeBaseUpdate;

    public void update() {
        ossService.listFiles("oss-filelist");
        knowledgeBaseUpdate.updateKnowledgeBase("audiolist.txt", "67jyw0wcx2", "file_3c10dc3fa1284c9a95c12a09e5a26524_10078838");
    }
}
