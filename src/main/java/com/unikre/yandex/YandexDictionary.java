package com.unikre.yandex;

import com.unikre.yandex.definition.Definition;
import com.unikre.yandex.definition.Example;
import com.unikre.yandex.definition.Mean;
import com.unikre.yandex.definition.Synonym;
import com.unikre.yandex.definition.Translation;
import com.unikre.yandex.http.YandexExecutor;
import com.unikre.yandex.http.YandexService;
import com.unikre.yandex.params.ApiVersion;
import com.unikre.yandex.params.Language;
import com.unikre.yandex.params.LookupFlag;
import com.unikre.yandex.params.RequestInterface;
import okhttp3.ResponseBody;
import org.json.JSONArray;
import org.json.JSONObject;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class YandexDictionary extends YandexExecutor {

    public YandexDictionary(String apiKey) {
        super(apiKey);
        setApiVersion(ApiVersion.DICTIONARY_LATEST);
        setRequestInterface(RequestInterface.DICTIONARY_JSON);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://dictionary.yandex.net")
                .build();
        yandexService = retrofit.create(YandexService.class);
    }

    private Map<Language, List<Language>> parseSupportedTranslateDirections(Response<ResponseBody> response) throws Exception {
        validateResponse(response);

        JSONArray jsonArray = new JSONArray(response.body().string());

        Map<Language, List<Language>> map = new HashMap<>();

        for (int i = 0; i < jsonArray.length(); i++) {
            String[] pairs = jsonArray.getString(i).split("-");

            Language from = Language.byCode(pairs[0]);
            Language to = Language.byCode(pairs[1]);

            List<Language> toLanguages = map.get(from);
            if (toLanguages == null) {
                toLanguages = new ArrayList<>();
            }
            toLanguages.add(to);

            map.put(from, toLanguages);
        }
        return map;
    }

    public Map<Language, List<Language>> getSupportedTranslateDirections() throws Exception {
        Call<ResponseBody> call = yandexService.getSupportedTranslateDirections(getApiVersion(),
                getRequestInterface(),
                getApiKey());

        return parseSupportedTranslateDirections(call.execute());
    }

    public void getSupportedTranslateDirections(final YandexCallback<Map<Language, List<Language>>> callback) throws Exception {
        Call<ResponseBody> call = yandexService.getSupportedTranslateDirections(getApiVersion(),
                getRequestInterface(),
                getApiKey());

        Callback<ResponseBody> genericCallback = new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    Map<Language, List<Language>> result = parseSupportedTranslateDirections(response);
                    callback.onResponse(result);
                } catch (Exception e) {
                    e.printStackTrace();
                    callback.onFailure(e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onFailure(t);
            }
        };

        call.enqueue(genericCallback);
    }

    private List<Definition> parseLookup(Response<ResponseBody> response) throws Exception {
        validateResponse(response);

        JSONObject jsonObject = new JSONObject(response.body().string());
        JSONArray defsArray = jsonObject.getJSONArray("def");

        return parseDefinitions(defsArray);
    }

    public List<Definition> lookup(String text, Language from, Language to, LookupFlag... flags) throws Exception {
        long flagsParam = 0;
        for (LookupFlag flag : flags) {
            if (flag != null) {
                flagsParam |= flag.getBitmask();
            }
        }

        Call<ResponseBody> call = yandexService.lookup(getApiVersion(),
                getRequestInterface(),
                getApiKey(),
                from == null ? to.toString() : from + "-" + to,
                text,
                flagsParam);

        return parseLookup(call.execute());
    }

    public List<Definition> lookup(String text, Language from, Language to) throws Exception {
        return lookup(text, from, to, (LookupFlag) null);
    }

    public void lookup(String text, Language from, Language to, final YandexCallback<List<Definition>> callback) {
        Call<ResponseBody> call = yandexService.lookup(getApiVersion(),
                getRequestInterface(),
                getApiKey(),
                from == null ? to.toString() : from + "-" + to,
                text,
                null);

        Callback<ResponseBody> genericCallback = new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    List<Definition> result = parseLookup(response);
                    callback.onResponse(result);
                } catch (Exception e) {
                    e.printStackTrace();
                    callback.onFailure(e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                callback.onFailure(t);
            }
        };

        call.enqueue(genericCallback);
    }

    /**********
     *        *
     **********/
    private List<Definition> parseDefinitions(JSONArray defArray) {
        List<Definition> definitions = new ArrayList<>();
        for (int i = 0; i < defArray.length(); i++) {
            JSONObject defObject = defArray.getJSONObject(i);

            Definition definition = new Definition();
            definition.setPos(defObject.getString("pos"));
            definition.setText(defObject.getString("text"));
            definition.setTranscription(defObject.getString("ts"));
            definition.setTranslations(parseTranslations(defObject.getJSONArray("tr")));

            definitions.add(definition);
        }
        return definitions;
    }

    private List<Translation> parseTranslations(JSONArray trArray) {
        List<Translation> translations = new ArrayList<>();
        for (int i = 0; i < trArray.length(); i++) {
            JSONObject trObject = trArray.getJSONObject(i);

            Translation translation = new Translation();
            translation.setPos(trObject.getString("pos"));
            translation.setText(trObject.getString("text"));

            if (trObject.has("gen")) {
                translation.setGen(trObject.getString("gen"));
            }

            if (trObject.has("ex")) {
                translation.setExamples(parseExamples(trObject.getJSONArray("ex")));
            }

            if (trObject.has("mean")) {
                translation.setMeans(parseMeans(trObject.getJSONArray("mean")));
            }

            if (trObject.has("syn")) {
                translation.setSynonyms(parseSyns(trObject.getJSONArray("syn")));
            }

            translations.add(translation);
        }
        return translations;
    }

    private List<Example> parseExamples(JSONArray exArray) {
        List<Example> examples = new ArrayList<>();
        for (int i = 0; i < exArray.length(); i++) {
            JSONObject exObject = exArray.getJSONObject(i);

            Example example = new Example();
            example.setText(exObject.getString("text"));
            example.setTranslation(exObject.getJSONArray("tr").getJSONObject(0).getString("text"));

            examples.add(example);
        }

        return examples;
    }

    private List<Mean> parseMeans(JSONArray meanArray) {
        List<Mean> means = new ArrayList<>();
        for (int i = 0; i < meanArray.length(); i++) {
            JSONObject meanObject = meanArray.getJSONObject(i);

            Mean mean = new Mean();
            mean.setText(meanObject.getString("text"));

            means.add(mean);
        }

        return means;
    }

    private List<Synonym> parseSyns(JSONArray synArray) {
        List<Synonym> syns = new ArrayList<>();
        for (int i = 0; i < synArray.length(); i++) {
            JSONObject synObject = synArray.getJSONObject(i);

            Synonym syn = new Synonym();
            syn.setText(synObject.getString("text"));
            syn.setPos(synObject.getString("pos"));
            if (synObject.has("gen")) syn.setGen(synObject.getString("gen"));

            syns.add(syn);
        }

        return syns;
    }

}