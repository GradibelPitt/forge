package forge.game.keyword;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Quest extends SimpleKeyword {
    private List<String> deckRequirements = Collections.emptyList();
    private String description = "";

    public Quest() { }

    @Override
    protected void parse(final String details) {
        final String[] fields = details.split(":", 2);
        if (fields.length < 2) {
            System.out.println("Did not parse a long enough value for Quest.");
            return;
        }
        deckRequirements = Arrays.stream(fields[0].split(";"))
                .map(String::trim)
                .filter(requirement -> !requirement.isEmpty())
                .toList();
        description = fields[1];
    }

    public List<String> getDeckRequirements() {
        return deckRequirements;
    }

    public String getDescription() {
        return description;
    }
}
