package ro.go.yomama.digistorage.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public class FileList {

    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class File {
        private String name;
        private String type;
        private Long modified;
        private Long size;
        private String contentType;
    }

    private List<File> files;
}
