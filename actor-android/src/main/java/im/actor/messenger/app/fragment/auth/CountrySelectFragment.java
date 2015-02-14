package im.actor.messenger.app.fragment.auth;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import im.actor.messenger.R;
import im.actor.messenger.app.base.BaseCompatFragment;
import im.actor.messenger.app.view.ViewHolder;
import im.actor.messenger.util.country.Country;
import im.actor.messenger.util.country.CountryDb;

import java.util.ArrayList;

public class CountrySelectFragment extends BaseCompatFragment {

    private ListView countriesListView;
    private CountryAdapter adapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_country_select, null);

        countriesListView = (ListView) v.findViewById(R.id.lv_countries);
        countriesListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (adapter != null) {
                    final Country country = adapter.getItem(position);
                    getActivity().setResult(Activity.RESULT_OK, new Intent()
                            .putExtra("country_id", country.fullNameRes)
                            .putExtra("country_code", country.phoneCode)
                            .putExtra("country_shortname", country.shortName));
                    getActivity().finish();
                }
            }
        });

        adapter = new CountryAdapter(CountryDb.getInstance().getCountries());
        countriesListView.setAdapter(adapter);

        return v;
    }

    private class CountryAdapter extends BaseAdapter {

        private ArrayList<Country> countries;
        private Context context;

        public CountryAdapter(ArrayList<Country> countries) {
            this.countries = countries;
            this.context = getActivity();
        }

        @Override
        public int getCount() {
            return countries.size();
        }

        @Override
        public Country getItem(int position) {
            return countries.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            CountryViewHolder holder;
            if (convertView == null || convertView.getTag() == null)
                holder = new CountryViewHolder();
            else
                holder = (CountryViewHolder) convertView.getTag();
            return holder.getView(convertView, getItem(position), position, parent, context);
        }

        private class CountryViewHolder extends ViewHolder<Country> {

            private TextView name;
            private TextView code;

            @Override
            public View init(Country data, ViewGroup parent, Context context) {
                View v = LayoutInflater.from(context).inflate(R.layout.adapter_country_select, null);

                name = (TextView) v.findViewById(R.id.tv_country_name);
                code = (TextView) v.findViewById(R.id.tv_country_code);

                return v;
            }

            @Override
            public void bind(Country data, int position, Context context) {
                name.setText(context.getString(data.fullNameRes));
                code.setText("+" + data.phoneCode);
            }

            @Override
            public void unbind() {
                name.setText("");
                code.setText("");
            }
        }

    }
}
