import {createClient} from 'npm:@supabase/supabase-js@2.57.0';
import {createOrderHandler} from './orders-handler.ts';
const db=createClient(Deno.env.get('SUPABASE_URL')!,Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!,{auth:{persistSession:false,autoRefreshToken:false}});
Deno.serve(createOrderHandler(db));
