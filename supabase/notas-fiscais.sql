-- Notas fiscais e boletos (rode UMA vez no SQL Editor do Supabase; pode rodar de novo sem problema)
create table if not exists public.invoices (
  id bigint primary key,
  number text not null default '',
  supplier text not null default '',
  issue_date bigint not null,
  items text not null default '[]',
  total bigint not null default 0,
  status text not null,
  received_at bigint,
  received_note text,
  note text,
  updated_at bigint not null default 0,
  server_updated_at timestamptz not null default now()
);

create table if not exists public.invoice_bills (
  id bigint primary key,
  invoice_id bigint not null,
  due_date bigint not null,
  amount bigint not null default 0,
  paid_at bigint,
  updated_at bigint not null default 0,
  server_updated_at timestamptz not null default now()
);

create index if not exists invoices_sua on public.invoices (server_updated_at);
create index if not exists invoice_bills_sua on public.invoice_bills (server_updated_at);

-- Ao registrar uma exclusão, apaga o registro correspondente.
create or replace function public.apply_deletion()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if new.table_name in ('products','sales','sale_items','stock_movements','scrap_prices','scrap_movements','charge_services','warranty_claims','expenses','sale_payments','invoices','invoice_bills') then
    execute format('delete from public.%I where id = $1', new.table_name) using new.record_id;
  end if;
  return new;
end $$;

do $$
declare t text;
begin
  foreach t in array array['invoices','invoice_bills'] loop
    execute format('drop trigger if exists touch_%1$s on public.%1$I', t);
    execute format('create trigger touch_%1$s before insert or update on public.%1$I for each row execute function public.touch_server_updated_at()', t);
    execute format('drop trigger if exists skip_deleted_%1$s on public.%1$I', t);
    execute format('create trigger skip_deleted_%1$s before insert on public.%1$I for each row execute function public.skip_if_deleted()', t);
    execute format('alter table public.%I enable row level security', t);
    execute format('drop policy if exists loja_acesso on public.%I', t);
    execute format('create policy loja_acesso on public.%I for all to authenticated using (true) with check (true)', t);
    execute format('revoke all on public.%I from anon', t);
    execute format('grant select, insert, update, delete on public.%I to authenticated', t);
  end loop;
end $$;

-- ---------- Função que devolve tudo o que mudou desde a última sincronização ----------
-- A sobreposição de 2 minutos garante que nada se perca entre gravações simultâneas.
create or replace function public.pull_changes(since timestamptz default null)
returns json
language sql
stable
security invoker
set search_path = public
as $$
  with c as (select coalesce(since - interval '2 minutes', '-infinity'::timestamptz) as t)
  select json_build_object(
    'now', now(),
    'products',        coalesce((select json_agg(x) from products x, c where x.server_updated_at > c.t), '[]'::json),
    'sales',           coalesce((select json_agg(x) from sales x, c where x.server_updated_at > c.t), '[]'::json),
    'sale_items',      coalesce((select json_agg(x) from sale_items x, c where x.server_updated_at > c.t), '[]'::json),
    'stock_movements', coalesce((select json_agg(x) from stock_movements x, c where x.server_updated_at > c.t), '[]'::json),
    'scrap_prices',    coalesce((select json_agg(x) from scrap_prices x, c where x.server_updated_at > c.t), '[]'::json),
    'scrap_movements', coalesce((select json_agg(x) from scrap_movements x, c where x.server_updated_at > c.t), '[]'::json),
    'charge_services', coalesce((select json_agg(x) from charge_services x, c where x.server_updated_at > c.t), '[]'::json),
    'warranty_claims', coalesce((select json_agg(x) from warranty_claims x, c where x.server_updated_at > c.t), '[]'::json),
    'expenses',        coalesce((select json_agg(x) from expenses x, c where x.server_updated_at > c.t), '[]'::json),
    'sale_payments',   coalesce((select json_agg(x) from sale_payments x, c where x.server_updated_at > c.t), '[]'::json),
    'invoices',        coalesce((select json_agg(x) from invoices x, c where x.server_updated_at > c.t), '[]'::json),
    'invoice_bills',   coalesce((select json_agg(x) from invoice_bills x, c where x.server_updated_at > c.t), '[]'::json),
    'deletions',       coalesce((select json_agg(x) from deletions x, c where x.server_updated_at > c.t), '[]'::json)
  );
$$;

revoke all on function public.pull_changes(timestamptz) from public, anon;
grant execute on function public.pull_changes(timestamptz) to authenticated;

