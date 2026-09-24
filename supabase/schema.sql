-- =====================================================================
-- Art das Baterias — banco na nuvem (Supabase)
-- Cole TODO este script no SQL Editor do Supabase e clique em "Run".
-- Pode ser executado mais de uma vez sem problemas.
-- =====================================================================

-- ---------- Tabelas (mesmas colunas do banco do celular) ----------
create table if not exists public.products (
  id bigint primary key,
  model text not null,
  cost bigint not null,
  price_pix bigint not null,
  price_debit bigint not null,
  price_credit bigint not null,
  stock integer not null default 0,
  min_stock integer not null default 2,
  created_at bigint not null default 0,
  amperage integer not null default 0,
  updated_at bigint not null default 0,
  server_updated_at timestamptz not null default now()
);

create table if not exists public.sales (
  id bigint primary key,
  date_time bigint not null,
  payment_method text not null,
  gross_amount bigint not null,
  discount bigint not null,
  final_amount bigint not null,
  total_cost bigint not null,
  gross_profit bigint not null,
  status text not null,
  canceled_at bigint,
  scrap_returned integer not null default 0,
  scrap_amperage integer,
  scrap_missing integer not null default 0,
  scrap_charge bigint not null default 0,
  updated_at bigint not null default 0,
  server_updated_at timestamptz not null default now()
);

create table if not exists public.sale_items (
  id bigint primary key,
  sale_id bigint not null,
  product_id bigint not null,
  model_snapshot text not null,
  quantity integer not null,
  unit_price bigint not null,
  unit_cost bigint not null,
  subtotal bigint not null,
  updated_at bigint not null default 0,
  server_updated_at timestamptz not null default now()
);

create table if not exists public.stock_movements (
  id bigint primary key,
  product_id bigint not null,
  date_time bigint not null,
  type text not null,
  quantity integer not null,
  stock_after integer not null default 0,
  sale_id bigint,
  note text,
  unit_cost bigint,
  updated_at bigint not null default 0,
  server_updated_at timestamptz not null default now()
);

create table if not exists public.scrap_prices (
  id bigint primary key,
  amperage integer not null,
  value bigint not null,
  updated_at bigint not null default 0,
  server_updated_at timestamptz not null default now()
);

create table if not exists public.scrap_movements (
  id bigint primary key,
  date_time bigint not null,
  type text not null,
  amperage integer not null,
  quantity integer not null,
  amount bigint not null default 0,
  sale_id bigint,
  note text,
  updated_at bigint not null default 0,
  server_updated_at timestamptz not null default now()
);

-- Exclusões (para apagar também nos outros celulares)
create table if not exists public.deletions (
  table_name text not null,
  record_id bigint not null,
  deleted_at bigint not null default 0,
  server_updated_at timestamptz not null default now(),
  primary key (table_name, record_id)
);

-- ---------- Índices para buscar só o que mudou ----------
create index if not exists products_sua on public.products (server_updated_at);
create index if not exists sales_sua on public.sales (server_updated_at);
create index if not exists sale_items_sua on public.sale_items (server_updated_at);
create index if not exists stock_movements_sua on public.stock_movements (server_updated_at);
create index if not exists scrap_prices_sua on public.scrap_prices (server_updated_at);
create index if not exists scrap_movements_sua on public.scrap_movements (server_updated_at);
create index if not exists deletions_sua on public.deletions (server_updated_at);

-- ---------- Gatilhos ----------
-- Marca o horário do servidor em cada gravação (usado para saber o que mudou).
create or replace function public.touch_server_updated_at()
returns trigger language plpgsql as $$
begin
  new.server_updated_at := now();
  return new;
end $$;

-- Não deixa "ressuscitar" um registro que já foi excluído em outro celular.
create or replace function public.skip_if_deleted()
returns trigger language plpgsql as $$
begin
  if exists (select 1 from public.deletions d where d.table_name = tg_table_name and d.record_id = new.id) then
    return null;
  end if;
  return new;
end $$;

-- Ao registrar uma exclusão, apaga o registro correspondente.
create or replace function public.apply_deletion()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if new.table_name in ('products','sales','sale_items','stock_movements','scrap_prices','scrap_movements') then
    execute format('delete from public.%I where id = $1', new.table_name) using new.record_id;
  end if;
  return new;
end $$;

do $$
declare t text;
begin
  foreach t in array array['products','sales','sale_items','stock_movements','scrap_prices','scrap_movements','deletions'] loop
    execute format('drop trigger if exists touch_%1$s on public.%1$I', t);
    execute format('create trigger touch_%1$s before insert or update on public.%1$I for each row execute function public.touch_server_updated_at()', t);
  end loop;
  foreach t in array array['products','sales','sale_items','stock_movements','scrap_prices','scrap_movements'] loop
    execute format('drop trigger if exists skip_deleted_%1$s on public.%1$I', t);
    execute format('create trigger skip_deleted_%1$s before insert on public.%1$I for each row execute function public.skip_if_deleted()', t);
  end loop;
end $$;

drop trigger if exists apply_deletion on public.deletions;
create trigger apply_deletion after insert or update on public.deletions
  for each row execute function public.apply_deletion();

-- ---------- Segurança: só a conta interna da loja (autenticada) acessa ----------
do $$
declare t text;
begin
  foreach t in array array['products','sales','sale_items','stock_movements','scrap_prices','scrap_movements','deletions'] loop
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
    'deletions',       coalesce((select json_agg(x) from deletions x, c where x.server_updated_at > c.t), '[]'::json)
  );
$$;

revoke all on function public.pull_changes(timestamptz) from public, anon;
grant execute on function public.pull_changes(timestamptz) to authenticated;

-- Pronto! Confira em Table Editor: devem aparecer 7 tabelas.
